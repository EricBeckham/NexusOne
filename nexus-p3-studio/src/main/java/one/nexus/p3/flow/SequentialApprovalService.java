package one.nexus.p3.flow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.common.time.Utc;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.StartCommand;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.engine.FlowEngineConfiguration;
import one.nexus.p3.flow.persist.JdbcProcessStore;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class SequentialApprovalService {
    static final int MAX_COMMENT_LENGTH = 2000;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final JdbcProcessStore store;
    private final AssigneeResolver assignees;
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;
    private final ProcessDefinitionPublishService publishing;
    private final ApprovalCommentStore comments;
    private final ObjectProvider<AuditPort> auditPort;

    public SequentialApprovalService(
            RuntimeService runtimeService,
            TaskService taskService,
            JdbcProcessStore store,
            AssigneeResolver assignees,
            PermissionModule permission,
            ObjectProvider<TenantMemberGrantSeeder> grantSeeder,
            ProcessDefinitionPublishService publishing,
            ApprovalCommentStore comments,
            ObjectProvider<AuditPort> auditPort
    ) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.store = store;
        this.assignees = assignees;
        this.permission = permission;
        this.grantSeeder = grantSeeder;
        this.publishing = publishing;
        this.comments = comments;
        this.auditPort = auditPort;
    }

    @Transactional
    public ProcessInstanceView start(StartCommand command) {
        return start(command, null);
    }

    /** Starts from the exact published definition version pinned by a product installation. */
    @Transactional
    public ProcessInstanceView start(StartCommand command, Integer pinnedVersion) {
        return startResolved(command, pinnedVersion, false, false);
    }

    /** Called by product submission after reading a server-owned installation pin/override. */
    @Transactional
    public ProcessInstanceView startProduct(StartCommand command, int version, boolean tenantOverride) {
        return startResolved(command, version, !tenantOverride, true);
    }

    private ProcessInstanceView startResolved(StartCommand command, Integer pinnedVersion, boolean platformDefault, boolean productRequest) {
        TenantContext tenant = requireTenant();
        requireText(command.businessKey(), "business_key is required");
        String processKey = command.processKey() == null || command.processKey().isBlank()
                ? FlowEngineConfiguration.PROCESS_KEY
                : command.processKey();
        ProcessDefinitionPublishService.PublishedDefinition published =
                platformDefault ? publishing.getPlatformDefault(processKey, pinnedVersion)
                        : productRequest ? publishing.get(processKey, pinnedVersion)
                        : FlowEngineConfiguration.PROCESS_KEY.equals(processKey) ? null
                        : pinnedVersion == null ? publishing.get(processKey) : publishing.get(processKey, pinnedVersion);
        guard("process.instance", "start");
        List<UUID> candidates = assignees.resolve(tenant.tenantId(), command.assigneeType(), command.assigneeRef());
        UUID assignee = candidates.getFirst();
        store.findInstanceByBusinessKey(tenant.tenantId(), command.businessKey()).ifPresent(existing -> {
            throw FlowException.conflict("business_key already used in this tenant");
        });
        Instant now = Utc.now();
        var processBuilder = runtimeService.createProcessInstanceBuilder();
        if (published == null) processBuilder.processDefinitionKey(processKey);
        else processBuilder.processDefinitionId(published.processDefinitionId());
        // Definition ownership and instance ownership differ for shared platform defaults.
        if (platformDefault) processBuilder.tenantId(tenant.tenantId().toString());
        ProcessInstance flowable = processBuilder
                .businessKey(command.businessKey())
                .variable("assignee", assignee.toString())
                .variable("initiator", tenant.personId().toString())
                .variable("assigneeType", command.assigneeType().name())
                .variable("outcome", "")
                .start();
        UUID instanceId = UuidV7.generate();
        ProcessInstanceView view = new ProcessInstanceView(
                instanceId,
                tenant.tenantId(),
                processKey,
                command.businessKey(),
                tenant.personId(),
                command.assigneeType(),
                command.assigneeRef(),
                flowable.getId(),
                ProcessStatus.RUNNING,
                now,
                null);
        try {
            store.insertInstance(view);
        } catch (DuplicateKeyException ex) {
            throw FlowException.conflict("business_key already used in this tenant");
        }
        Task task = taskService.createTaskQuery()
                .processInstanceId(flowable.getId())
                .singleResult();
        if (task == null) {
            throw FlowException.conflict("sequential process did not create a user task");
        }
        for (UUID candidate : candidates) {
            if (!candidate.equals(assignee)) {
                taskService.addCandidateUser(task.getId(), candidate.toString());
            }
        }
        store.insertTodo(new TodoView(
                UuidV7.generate(),
                tenant.tenantId(),
                instanceId,
                task.getId(),
                task.getName(),
                assignee,
                candidates,
                TodoStatus.PENDING,
                null,
                null,
                null,
                now,
                command.businessKey(),
                ProcessStatus.RUNNING,
                null));
        store.recordAction(tenant.tenantId(), instanceId, null, tenant.personId(), "START",
                "START:" + command.businessKey(), now);
        return view;
    }

    @Transactional
    public TodoView approve(UUID todoId) {
        return approve(todoId, null);
    }

    @Transactional
    public TodoView approve(UUID todoId, String comment) {
        return complete(todoId, "APPROVE", ProcessStatus.APPROVED, comment);
    }

    @Transactional
    public TodoView reject(UUID todoId) {
        return reject(todoId, null);
    }

    @Transactional
    public TodoView reject(UUID todoId, String comment) {
        return complete(todoId, "REJECT", ProcessStatus.REJECTED, comment);
    }

    @Transactional
    public ProcessInstanceView withdraw(UUID instanceId) {
        TenantContext tenant = requireTenant();
        ProcessInstanceView instance = store.findInstance(tenant.tenantId(), instanceId)
                .orElseThrow(() -> FlowException.notFound("process instance not found in current tenant"));
        String key = "WITHDRAW:" + instanceId;
        if (instance.status() == ProcessStatus.WITHDRAWN) {
            return instance;
        }
        if (instance.status() != ProcessStatus.RUNNING) {
            throw FlowException.notRunning("instance is " + instance.status());
        }
        if (!instance.initiatorPersonId().equals(tenant.personId())) {
            throw FlowException.forbidden("only the initiator may withdraw");
        }
        guard("process.instance", "withdraw");
        Instant now = Utc.now();
        runtimeService.deleteProcessInstance(instance.flowableInstanceId(), "withdraw");
        store.cancelPending(tenant.tenantId(), instanceId);
        store.finishInstance(tenant.tenantId(), instanceId, ProcessStatus.WITHDRAWN, now);
        if (!store.actionExists(tenant.tenantId(), key)) {
            store.recordAction(tenant.tenantId(), instanceId, null, tenant.personId(), "WITHDRAW", key, now);
        }
        return store.findInstance(tenant.tenantId(), instanceId).orElseThrow();
    }

    private TodoView complete(UUID todoId, String outcome, ProcessStatus endStatus, String comment) {
        TenantContext tenant = requireTenant();
        TodoView todo = store.findTodo(tenant.tenantId(), todoId)
                .orElseThrow(() -> FlowException.notFound("todo not found in current tenant"));
        String key = outcome + ":" + todoId;
        if (todo.status() == TodoStatus.DONE && outcome.equals(todo.outcome())) {
            return comments.attach(todo);
        }
        if (todo.status() != TodoStatus.PENDING) {
            throw FlowException.conflict("todo is " + todo.status());
        }
        if (!todo.candidatePersonIds().contains(tenant.personId())
                && (todo.assigneePersonId() == null || !todo.assigneePersonId().equals(tenant.personId()))) {
            throw FlowException.forbidden("current person is not a candidate for this todo");
        }
        String normalized = normalizeComment(comment);
        guard("process.task", outcome.toLowerCase());
        Instant now = Utc.now();
        ProcessInstanceView instance = store.findInstance(tenant.tenantId(), todo.instanceId())
                .orElseThrow(() -> FlowException.notFound("process instance not found in current tenant"));
        taskService.setAssignee(todo.flowableTaskId(), tenant.personId().toString());
        if (normalized != null) {
            comments.save(todo.flowableTaskId(), instance.flowableInstanceId(), normalized);
        }
        taskService.complete(todo.flowableTaskId(), Map.of("outcome", outcome));
        store.completeTodo(tenant.tenantId(), todoId, tenant.personId(), outcome, now);
        store.finishInstance(tenant.tenantId(), todo.instanceId(), endStatus, now);
        if (!store.actionExists(tenant.tenantId(), key)) {
            store.recordAction(tenant.tenantId(), todo.instanceId(), todoId, tenant.personId(), outcome, key, now);
        }
        auditDecision(tenant, todo, outcome, normalized);
        TodoView saved = store.findTodo(tenant.tenantId(), todoId).orElseThrow();
        return normalized == null ? saved : saved.withComment(normalized);
    }

    static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw FlowException.badRequest("comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        return trimmed;
    }

    private void auditDecision(TenantContext tenant, TodoView todo, String outcome, String comment) {
        AuditPort port = auditPort.getIfAvailable();
        if (port == null) {
            return;
        }
        String action = "APPROVE".equals(outcome) ? "approve" : "reject";
        port.append(AuditCaptures.approval(
                "p3.process.task." + action,
                true,
                tenant.tenantId(),
                tenant.personId(),
                todo.id().toString(),
                action,
                null,
                Map.of(
                        "instance_id", todo.instanceId().toString(),
                        "comment_present", comment == null ? "false" : "true",
                        "comment_chars", Integer.toString(comment == null ? 0 : comment.length()))));
    }

    private void guard(String resource, String action) {
        try {
            // Same demo seed as FormSurfaceGuard / PermissionDataScopeHook /
            // FlowAuthFilter. In-process start (G18 rollback probe) never hits
            // those HTTP filters.
            TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
            SurfaceGuard.require(permission, resource, action);
        } catch (PermissionException ex) {
            throw FlowException.forbidden(ex.getMessage());
        }
    }

    private static TenantContext requireTenant() {
        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            throw FlowException.missingContext();
        }
        return tenant;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw FlowException.badRequest(message);
        }
    }
}
