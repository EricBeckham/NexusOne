package one.nexus.p3.path;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.time.Utc;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.notification.ApprovalNotificationPublisher;
import one.nexus.notification.BuiltinTemplates;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.FlowException;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.StartCommand;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.ProcessStatus;
import one.nexus.p3.flow.SequentialApprovalService;
import one.nexus.p3.flow.TodoStatus;
import one.nexus.p3.flow.persist.JdbcProcessStore;
import one.nexus.p3.path.BusinessPathRecords.DecisionView;
import one.nexus.p3.path.BusinessPathRecords.PathView;
import one.nexus.p3.path.BusinessPathRecords.SubmitStartCommand;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.infra.permission.FormSurfaceGuard;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.studio.form.FormService;
import one.nexus.p3.studio.form.FormSubmission;
import one.nexus.p3.studio.form.FormVersion;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published form → SchemaService physical row → Flowable sequential approval
 * in the same Spring TX → Outbox {@code notify.inbox} → M0-11 audit hash chain.
 * Shared by G18 leave and the G7 catalog types.
 */
@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
@ConditionalOnBean({SequentialApprovalService.class, LedgerRecordSink.class})
public class BusinessPathService {
    static final String SUBMIT_START_SCOPE = "p3.path.submit-start";
    static final int MAX_IDEMPOTENCY_KEY = 128;

    private final FormService forms;
    private final SequentialApprovalService approvals;
    private final JdbcProcessStore processStore;
    private final LedgerRecordSink ledgerRecords;
    private final ObjectProvider<OutboxPort> outbox;
    private final ObjectProvider<ApprovalNotificationPublisher> notifications;
    private final ObjectProvider<AuditPort> audits;
    private final ObjectProvider<IdempotencyStore> idempotency;
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public BusinessPathService(
            FormService forms,
            SequentialApprovalService approvals,
            JdbcProcessStore processStore,
            LedgerRecordSink ledgerRecords,
            ObjectProvider<OutboxPort> outbox,
            ObjectProvider<ApprovalNotificationPublisher> notifications,
            ObjectProvider<AuditPort> audits,
            ObjectProvider<IdempotencyStore> idempotency,
            PermissionModule permission,
            ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.approvals = Objects.requireNonNull(approvals, "approvals");
        this.processStore = Objects.requireNonNull(processStore, "processStore");
        this.ledgerRecords = Objects.requireNonNull(ledgerRecords, "ledgerRecords");
        this.outbox = outbox;
        this.notifications = notifications;
        this.audits = audits;
        this.idempotency = idempotency;
        this.permission = Objects.requireNonNull(permission, "permission");
        this.grantSeeder = grantSeeder;
    }

    /** FA4: optional post-completion actions; absent on hosts without them. */
    private one.nexus.p3.completion.CompletionActionTrigger completionActions;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void useCompletionActions(one.nexus.p3.completion.CompletionActionTrigger completionActions) {
        this.completionActions = completionActions;
    }

    @Transactional
    public PathView submitAndStart(SubmitStartCommand command) {
        requireIsolation();
        one.nexus.p2.kernel.TenantContext iam = requireIam();
        requireFormWriteWhenFormPresent(command.formId());
        String key = normalizeIdempotencyKey(command.idempotencyKey());
        String storedKey = key == null ? null : storedIdempotencyKey(iam.tenantId(), key);
        if (storedKey != null) {
            IdempotencyStore store = requireIdempotency();
            IdempotencyStore.Claim claim = store.claimInTransaction(
                    SUBMIT_START_SCOPE, storedKey, iam.tenantId().toString(), isolationTrace());
            if (claim == IdempotencyStore.Claim.DUPLICATE) {
                String ref = store.resultRef(SUBMIT_START_SCOPE, storedKey)
                        .orElseThrow(() -> new NexusException(
                                ErrorCode.API_CONFLICT,
                                409,
                                "submit-start replay is not ready",
                                Map.of("idempotency_key", key)));
                return loadExisting(UUID.fromString(ref), true);
            }
        }
        PathView started = startFresh(command, iam);
        if (storedKey != null) {
            requireIdempotency().remember(SUBMIT_START_SCOPE, storedKey, started.recordId().toString());
        }
        return started;
    }

    /**
     * Form-fill HTTP {@code submit-start} is a dual-check when {@code form_id}
     * is present: {@code form.write} here (same cell as
     * {@link FormSurfaceGuard#requireFill}), then {@code process.instance}/
     * {@code start} inside {@link SequentialApprovalService#start}. Deny
     * happens before {@link FormService#submit} and Flowable start. Isolation-only
     * callers with no P2 {@code TenantContext} stay tenant-only. Demo
     * {@code tenant_member} seed may still run so the closed G18 leave walk
     * keeps working.
     */
    private void requireFormWriteWhenFormPresent(UUID formId) {
        if (formId == null || TenantContextHolder.get() == null) {
            return;
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        SurfaceGuard.require(permission, FormSurfaceGuard.RESOURCE, FormSurfaceGuard.WRITE);
    }

    private PathView startFresh(SubmitStartCommand command, one.nexus.p2.kernel.TenantContext iam) {
        FormSubmission submission = forms.submit(command.formId(), command.values());
        UUID ledgerId = submission.ledgerId();
        UUID recordId = submission.ledgerRecordId();
        if (recordId == null || ledgerId == null) {
            throw new NexusException(
                    ErrorCode.FORM_SUBMIT_INVALID,
                    409,
                    "submit did not write a physical ledger row",
                    Map.of());
        }
        FormVersion running = forms.running(command.formId());
        String ledgerCode = FormService.uniqueLedgerCode(running.snapshot());
        ledgerRecords.writeApprovalStatus(ledgerId, recordId, LedgerRecordSink.IN_APPROVAL);
        ProcessInstanceView process = approvals.start(new StartCommand(
                command.processKey(),
                submission.ledgerRecordId().toString(),
                command.assigneeType(),
                command.assigneeRef()));
        TodoView todo = processStore.todosForInstance(process.tenantId(), process.id()).stream()
                .filter(item -> item.status() == TodoStatus.PENDING)
                .findFirst()
                .orElse(null);
        String title = String.valueOf(command.values() == null ? "" : command.values().getOrDefault("title", "form"));
        enqueueAssigned(todo, iam.personId(), title);
        audit(
                AuditCaptures.data(
                        "p3.path.submitted",
                        true,
                        submission.tenantId(),
                        iam.personId(),
                        "ledger_record",
                        submission.ledgerRecordId().toString(),
                        "form submit wrote physical row",
                        isolationTrace(),
                        Map.of(
                                "form_id", command.formId().toString(),
                                "ledger_id", ledgerId.toString(),
                                "process_id", process.id().toString())),
                AuditCaptures.approval(
                        "p3.approval.started",
                        true,
                        submission.tenantId(),
                        iam.personId(),
                        process.id().toString(),
                        "sequential approval started",
                        isolationTrace(),
                        Map.of("record_id", submission.ledgerRecordId().toString())));
        Map<String, Object> row = ledgerRecords.get(ledgerId, submission.ledgerRecordId());
        return new PathView(
                submission.submissionId(),
                submission.formId(),
                ledgerId,
                submission.ledgerRecordId(),
                ledgerCode,
                ledgerRecords.physicalTable(submission.tenantId(), ledgerId).orElse(""),
                row.get(LedgerRecordSink.APPROVAL_STATUS),
                submission,
                process,
                todo,
                Utc.now(),
                false,
                row.get("version"));
    }

    private PathView loadExisting(UUID recordId, boolean replay) {
        FormSubmission submission = forms.findByLedgerRecord(recordId)
                .orElseThrow(() -> new NexusException(
                        ErrorCode.FORM_SUBMIT_INVALID,
                        409,
                        "submit-start key points at a missing record",
                        Map.of("record_id", recordId.toString())));
        UUID ledgerId = submission.ledgerId();
        FormVersion running = forms.running(submission.formId());
        String ledgerCode = FormService.uniqueLedgerCode(running.snapshot());
        ProcessInstanceView process = processStore
                .findInstanceByBusinessKey(submission.tenantId(), recordId.toString())
                .orElse(null);
        TodoView todo = process == null
                ? null
                : processStore.todosForInstance(process.tenantId(), process.id()).stream()
                        .filter(item -> item.status() == TodoStatus.PENDING)
                        .findFirst()
                        .orElseGet(() -> processStore.todosForInstance(process.tenantId(), process.id()).stream()
                                .findFirst()
                                .orElse(null));
        Map<String, Object> row = ledgerRecords.get(ledgerId, recordId);
        return new PathView(
                submission.submissionId(),
                submission.formId(),
                ledgerId,
                recordId,
                ledgerCode,
                ledgerRecords.physicalTable(submission.tenantId(), ledgerId).orElse(""),
                row.get(LedgerRecordSink.APPROVAL_STATUS),
                submission,
                process,
                todo,
                Utc.now(),
                replay,
                row.get("version"));
    }

    private IdempotencyStore requireIdempotency() {
        IdempotencyStore store = idempotency == null ? null : idempotency.getIfAvailable();
        if (store == null) {
            throw new NexusException(
                    ErrorCode.API_INTERNAL, 500, "idempotency store is required for submit-start keys", Map.of());
        }
        return store;
    }

    static String normalizeIdempotencyKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY) {
            throw new NexusException(
                    ErrorCode.API_BAD_REQUEST,
                    400,
                    "idempotency key must be 1.." + MAX_IDEMPOTENCY_KEY + " characters",
                    Map.of("length", key.length()));
        }
        return key;
    }

    static String storedIdempotencyKey(UUID tenantId, String clientKey) {
        return tenantId + ":" + clientKey;
    }

    @Transactional
    public DecisionView approve(UUID todoId) {
        return approve(todoId, null);
    }

    @Transactional
    public DecisionView approve(UUID todoId, String comment) {
        return complete(todoId, "APPROVE", LedgerRecordSink.APPROVED, BuiltinTemplates.APPROVAL_APPROVED, comment);
    }

    @Transactional
    public DecisionView reject(UUID todoId) {
        return reject(todoId, null);
    }

    @Transactional
    public DecisionView reject(UUID todoId, String comment) {
        return complete(todoId, "REJECT", LedgerRecordSink.REJECTED, BuiltinTemplates.APPROVAL_REJECTED, comment);
    }

    @Transactional
    public DecisionView withdraw(UUID instanceId) {
        requireIsolation();
        one.nexus.p2.kernel.TenantContext iam = requireIam();
        ProcessInstanceView before = processStore.findInstance(iam.tenantId(), instanceId)
                .orElseThrow(() -> FlowException.notFound("process instance not found in current tenant"));
        boolean already = before.status() == ProcessStatus.WITHDRAWN;
        ProcessInstanceView process = approvals.withdraw(instanceId);
        UUID recordId = parseRecordId(process.businessKey());
        UUID ledgerId = ledgerIdForRecord(recordId);
        if (!already) {
            ledgerRecords.writeApprovalStatus(ledgerId, recordId, LedgerRecordSink.WITHDRAWN);
            audit(AuditCaptures.approval(
                    "p3.approval.withdrawn",
                    true,
                    iam.tenantId(),
                    iam.personId(),
                    process.id().toString(),
                    "withdraw",
                    isolationTrace(),
                    Map.of("record_id", recordId.toString())));
        }
        Map<String, Object> row = ledgerRecords.get(ledgerId, recordId);
        return new DecisionView(
                ledgerId,
                recordId,
                row.get(LedgerRecordSink.APPROVAL_STATUS),
                ledgerRecords.physicalTable(iam.tenantId(), ledgerId).orElse(""),
                process,
                null);
    }

    private DecisionView complete(UUID todoId, String outcome, String status, String template, String comment) {
        requireIsolation();
        one.nexus.p2.kernel.TenantContext iam = requireIam();
        TodoView before = processStore.findTodo(iam.tenantId(), todoId)
                .orElseThrow(() -> FlowException.notFound("todo not found in current tenant"));
        boolean replay = before.status() == TodoStatus.DONE && outcome.equals(before.outcome());
        TodoView todo = "APPROVE".equals(outcome) ? approvals.approve(todoId, comment) : approvals.reject(todoId, comment);
        ProcessInstanceView process = processStore.findInstance(iam.tenantId(), todo.instanceId()).orElseThrow();
        UUID recordId = parseRecordId(process.businessKey());
        UUID ledgerId = ledgerIdForRecord(recordId);
        if (!replay) {
            ledgerRecords.writeApprovalStatus(ledgerId, recordId, status);
            // FA4: in the same transaction as the terminal status, record "post-completion actions are due".
            if (completionActions != null) {
                completionActions.onCompleted(iam.tenantId(), ledgerId, recordId, process.id(), process.processKey(),
                        status, process.initiatorPersonId(), iam.personId());
            }
            enqueueDecision(process, iam.personId(), template);
            audit(
                    AuditCaptures.data(
                            "p3.path.decided",
                            true,
                            iam.tenantId(),
                            iam.personId(),
                            "ledger_record",
                            recordId.toString(),
                            outcome,
                            isolationTrace(),
                            Map.of("ledger_id", ledgerId.toString(), "todo_id", todoId.toString())),
                    AuditCaptures.approval(
                            "APPROVE".equals(outcome) ? "p3.approval.approved" : "p3.approval.rejected",
                            true,
                            iam.tenantId(),
                            iam.personId(),
                            todoId.toString(),
                            outcome,
                            isolationTrace(),
                            Map.of("record_id", recordId.toString())));
        }
        Map<String, Object> row = ledgerRecords.get(ledgerId, recordId);
        return new DecisionView(
                ledgerId,
                recordId,
                row.get(LedgerRecordSink.APPROVAL_STATUS),
                ledgerRecords.physicalTable(iam.tenantId(), ledgerId).orElse(""),
                process,
                todo);
    }

    private UUID ledgerIdForRecord(UUID recordId) {
        return forms.findByLedgerRecord(recordId)
                .map(FormSubmission::ledgerId)
                .orElseThrow(() -> new NexusException(
                        ErrorCode.FORM_SUBMIT_INVALID, 404, "no form submission for ledger record", Map.of()));
    }

    private void enqueueAssigned(TodoView todo, UUID actor, String title) {
        if (todo == null || todo.assigneePersonId() == null) {
            return;
        }
        enqueue(
                BuiltinTemplates.APPROVAL_ASSIGNED,
                todo.assigneePersonId().toString(),
                title,
                actor.toString());
    }

    private void enqueueDecision(ProcessInstanceView process, UUID actor, String template) {
        enqueue(template, process.initiatorPersonId().toString(), process.businessKey(), actor.toString());
    }

    private void enqueue(String template, String recipient, String title, String actor) {
        ApprovalNotificationPublisher publisher = notifications == null ? null : notifications.getIfAvailable();
        if (publisher != null) {
            if (BuiltinTemplates.APPROVAL_ASSIGNED.equals(template)) {
                publisher.taskAssigned(recipient, title, actor);
            } else if (BuiltinTemplates.APPROVAL_APPROVED.equals(template)) {
                publisher.approved(recipient, title, actor);
            } else {
                publisher.rejected(recipient, title, actor);
            }
            return;
        }
        OutboxPort port = outbox == null ? null : outbox.getIfAvailable();
        if (port != null) {
            port.append(OutboxAppendCommand.notifyInbox(
                    template,
                    "{\"template\":\"" + template
                            + "\",\"recipientSubjectId\":\"" + recipient
                            + "\",\"title\":\"" + escape(title) + "\"}"));
        }
    }

    private void audit(one.nexus.common.audit.AuditEvent... events) {
        AuditPort port = audits == null ? null : audits.getIfAvailable();
        if (port == null) {
            return;
        }
        for (one.nexus.common.audit.AuditEvent event : events) {
            port.append(event);
        }
    }

    private static UUID parseRecordId(String businessKey) {
        try {
            return UUID.fromString(businessKey);
        } catch (RuntimeException ex) {
            throw new NexusException(
                    ErrorCode.FORM_SUBMIT_INVALID, 400, "business_key is not a ledger record id", Map.of());
        }
    }

    private static TenantContext requireIsolation() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required");
        }
        return tenant;
    }

    private static one.nexus.p2.kernel.TenantContext requireIam() {
        one.nexus.p2.kernel.TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            throw FlowException.missingContext();
        }
        return tenant;
    }

    private static String isolationTrace() {
        AccessContext context = AccessContextHolder.currentOrNull();
        return context == null ? "unknown" : context.traceId();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
