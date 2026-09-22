package one.nexus.p3.studio.form;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.id.UuidV7;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.SideEffectGuard;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.studio.meta.LedgerMetadataPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Draft → Validate → Publish → Running. Publish failures roll back so the running pointer is unchanged.
 */
public final class FormService {
    private final FormStore store;
    private final LedgerMetadataPort metadata;
    private final PublishHook publishHook;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    private final LedgerRecordSink ledgerRecords;

    public FormService(
            FormStore store,
            LedgerMetadataPort metadata,
            PublishHook publishHook,
            Clock clock,
            PlatformTransactionManager transactions) {
        this(store, metadata, publishHook, clock, transactions, null);
    }

    public FormService(
            FormStore store,
            LedgerMetadataPort metadata,
            PublishHook publishHook,
            Clock clock,
            PlatformTransactionManager transactions,
            LedgerRecordSink ledgerRecords) {
        this.store = Objects.requireNonNull(store, "store");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.publishHook = publishHook == null ? PublishHook.noop() : publishHook;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = transactions;
        this.ledgerRecords = ledgerRecords;
    }

    public FormRecord create(String appCode, String formCode, String name, String description, List<FormBinding> bindings) {
        TenantContext tenant = requireTenant();
        BindingValidator.requireCode(appCode, "app_code");
        BindingValidator.requireCode(formCode, "form_code");
        if (name == null || name.isBlank()) {
            throw new NexusException(ErrorCode.FORM_INVALID, 400, "name is required");
        }
        UUID tenantId = UUID.fromString(tenant.tenantId());
        if (store.findByCode(tenantId, formCode).isPresent()) {
            throw new NexusException(ErrorCode.FORM_CODE_CONFLICT, 409, "form_code already exists in tenant");
        }
        Instant now = Instant.now(clock);
        String actor = tenant.actor().actorId();
        FormRecord form = new FormRecord(
                UuidV7.generate(),
                tenantId,
                appCode,
                formCode,
                name.trim(),
                description,
                FormStatus.DRAFT,
                null,
                List.copyOf(bindings == null ? List.of() : bindings),
                now,
                actor,
                now,
                actor);
        store.insert(form);
        return form;
    }

    public FormRecord update(UUID formId, String name, String description, List<FormBinding> bindings) {
        TenantContext tenant = requireTenant();
        FormRecord current = load(tenant, formId);
        Instant now = Instant.now(clock);
        String nextName = name == null || name.isBlank() ? current.name() : name.trim();
        FormRecord updated = current
                .withMeta(nextName, description == null ? current.description() : description, now, tenant.actor().actorId())
                .withDraft(FormStatus.DRAFT, bindings == null ? current.draftBindings() : bindings, now, tenant.actor().actorId());
        store.update(updated);
        return updated;
    }

    public FormRecord get(UUID formId) {
        return load(requireTenant(), formId);
    }

    public List<FormRecord> list() {
        return store.list(UUID.fromString(requireTenant().tenantId()));
    }

    public FormRecord validate(UUID formId) {
        TenantContext tenant = requireTenant();
        FormRecord form = load(tenant, formId);
        BindingValidator.validateAgainstMetadata(form.tenantId(), form.draftBindings(), metadata);
        Instant now = Instant.now(clock);
        FormRecord validated = form.withDraft(FormStatus.VALIDATED, form.draftBindings(), now, tenant.actor().actorId());
        store.update(validated);
        store.appendLog(log(
                validated,
                form.runningVersion(),
                form.draftStatus().name(),
                FormStatus.VALIDATED.name(),
                "ACCEPTED",
                "validated against ledger/field codes",
                Map.of(),
                tenant.traceId(),
                now));
        return validated;
    }

    public FormRecord publish(UUID formId, String notes) {
        TenantContext tenant = requireTenant();
        FormRecord before = load(tenant, formId);
        if (before.draftStatus() != FormStatus.VALIDATED) {
            throw new NexusException(
                    ErrorCode.FORM_INVALID_STATE,
                    409,
                    "publish requires VALIDATED; current=" + before.draftStatus().name());
        }
        Integer previousRunning = before.runningVersion();
        try {
            return inPublishTransaction(() -> activate(tenant, formId, notes));
        } catch (NexusException ex) {
            if (ex.errorCode().equals(ErrorCode.FORM_INVALID_STATE)
                    || ex.errorCode().equals(ErrorCode.FORM_VALIDATE_FAILED)
                    || ex.errorCode().equals(ErrorCode.FORM_PHYSICAL_BINDING)
                    || ex.errorCode().equals(ErrorCode.FORM_BINDING_INVALID)) {
                throw ex;
            }
            recordRollback(before, previousRunning, tenant, ex.getMessage());
            throw new NexusException(
                    ErrorCode.FORM_PUBLISH_FAILED,
                    409,
                    "publish rolled back; running version unchanged",
                    Map.of("running_version", previousRunning == null ? 0 : previousRunning, "cause", ex.getMessage()));
        } catch (RuntimeException ex) {
            recordRollback(before, previousRunning, tenant, ex.getMessage());
            throw new NexusException(
                    ErrorCode.FORM_PUBLISH_FAILED,
                    409,
                    "publish rolled back; running version unchanged",
                    Map.of(
                            "running_version",
                            previousRunning == null ? 0 : previousRunning,
                            "cause",
                            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    public FormVersion running(UUID formId) {
        TenantContext tenant = requireTenant();
        FormRecord form = load(tenant, formId);
        return store.findRunning(form.tenantId(), form.formId())
                .orElseThrow(() -> new NexusException(ErrorCode.FORM_NOT_RUNNING, 409, "form has no running version"));
    }

    public List<FormVersion> versions(UUID formId) {
        TenantContext tenant = requireTenant();
        FormRecord form = load(tenant, formId);
        return store.versions(form.tenantId(), form.formId());
    }

    public List<PublishLog> publishLog(UUID formId) {
        TenantContext tenant = requireTenant();
        FormRecord form = load(tenant, formId);
        return store.logs(form.tenantId(), form.formId());
    }

    public FormSubmission submit(UUID formId, Map<String, Object> values) {
        TenantContext tenant = requireTenant();
        if (transactions != null) {
            TransactionTemplate template = new TransactionTemplate(transactions);
            return template.execute(status -> persistSubmission(tenant, formId, values));
        }
        return persistSubmission(tenant, formId, values);
    }

    public List<FormSubmission> submissions(UUID formId) {
        TenantContext tenant = requireTenant();
        FormRecord form = load(tenant, formId);
        return store.submissions(form.tenantId(), form.formId());
    }

    public java.util.Optional<FormSubmission> findByLedgerRecord(UUID ledgerRecordId) {
        TenantContext tenant = requireTenant();
        return store.findByLedgerRecord(UUID.fromString(tenant.tenantId()), ledgerRecordId);
    }

    private FormRecord activate(TenantContext tenant, UUID formId, String notes) {
        FormRecord form = load(tenant, formId);
        if (form.draftStatus() != FormStatus.VALIDATED) {
            throw new NexusException(
                    ErrorCode.FORM_INVALID_STATE, 409, "publish requires VALIDATED; current=" + form.draftStatus());
        }
        BindingValidator.validateAgainstMetadata(form.tenantId(), form.draftBindings(), metadata);
        int versionNo = store.nextVersionNo(form.tenantId(), form.formId());
        Instant now = Instant.now(clock);
        FormSnapshot snapshot = FormSnapshot.of(form, versionNo);
        FormVersion version = new FormVersion(
                UuidV7.generate(),
                form.formId(),
                form.tenantId(),
                versionNo,
                VersionStatus.PUBLISHING,
                snapshot,
                notes,
                now,
                tenant.actor().actorId());
        store.insertVersion(version);
        publishHook.beforeActivate(snapshot);
        store.findRunning(form.tenantId(), form.formId())
                .ifPresent(running -> store.updateVersion(running.withStatus(VersionStatus.SUPERSEDED)));
        store.updateVersion(version.withStatus(VersionStatus.RUNNING));
        FormRecord published = form.withRunning(versionNo, now, tenant.actor().actorId());
        store.update(published);
        store.appendLog(log(
                published,
                versionNo,
                FormStatus.VALIDATED.name(),
                VersionStatus.RUNNING.name(),
                "ACCEPTED",
                "published version " + versionNo,
                Map.of("notes", notes == null ? "" : notes),
                tenant.traceId(),
                now));
        return published;
    }

    private FormRecord inPublishTransaction(Supplier<FormRecord> action) {
        if (transactions != null) {
            TransactionTemplate template = new TransactionTemplate(transactions);
            return template.execute(status -> action.get());
        }
        final FormRecord[] result = new FormRecord[1];
        store.runAtomically(() -> result[0] = action.get());
        return result[0];
    }

    private void recordRollback(FormRecord before, Integer previousRunning, TenantContext tenant, String cause) {
        store.appendLog(log(
                before,
                previousRunning,
                FormStatus.VALIDATED.name(),
                VersionStatus.FAILED.name(),
                "ROLLED_BACK",
                cause == null ? "publish failed" : cause,
                Map.of("running_version_unchanged", previousRunning == null ? 0 : previousRunning),
                tenant.traceId(),
                Instant.now(clock)));
    }

    private static PublishLog log(
            FormRecord form,
            Integer versionNo,
            String from,
            String to,
            String outcome,
            String message,
            Map<String, Object> details,
            String traceId,
            Instant at) {
        return new PublishLog(
                UuidV7.generate(),
                form.formId(),
                form.tenantId(),
                versionNo,
                from,
                to,
                outcome,
                message,
                details,
                traceId,
                at);
    }

    private FormSubmission persistSubmission(TenantContext tenant, UUID formId, Map<String, Object> values) {
        FormRecord form = load(tenant, formId);
        FormVersion running = store.findRunning(form.tenantId(), form.formId())
                .orElseThrow(() -> new NexusException(ErrorCode.FORM_NOT_RUNNING, 409, "form has no running version"));
        Map<String, Object> payload = values == null ? Map.of() : new LinkedHashMap<>(values);
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FormBinding binding : running.snapshot().bindings()) {
            Object value = payload.get(binding.fieldCode());
            if (binding.required() && (value == null || String.valueOf(value).isBlank())) {
                errors.add(Map.of("field_code", binding.fieldCode(), "reason", "required"));
            }
        }
        for (String key : payload.keySet()) {
            boolean known = running.snapshot().bindings().stream().anyMatch(binding -> binding.fieldCode().equals(key));
            if (!known) {
                errors.add(Map.of("field_code", key, "reason", "not bound on running form"));
            }
        }
        if (!errors.isEmpty()) {
            throw new NexusException(ErrorCode.FORM_SUBMIT_INVALID, 400, "submission rejected", Map.of("errors", errors));
        }
        UUID recordId = null;
        UUID ledgerId = null;
        if (ledgerRecords != null) {
            String ledgerCode = uniqueLedgerCode(running.snapshot());
            ledgerId = ledgerRecords.findLedgerId(form.tenantId(), ledgerCode).orElse(null);
            recordId = ledgerRecords.insert(ledgerCode, ledgerValues(running.snapshot(), payload));
        }
        FormSubmission submission = new FormSubmission(
                UuidV7.generate(),
                form.formId(),
                form.tenantId(),
                running.versionNo(),
                Map.copyOf(payload),
                Instant.now(clock),
                tenant.actor().actorId(),
                ledgerId,
                recordId);
        store.insertSubmission(submission);
        return submission;
    }

    public static String uniqueLedgerCode(FormSnapshot snapshot) {
        List<String> codes = snapshot.bindings().stream().map(FormBinding::ledgerCode).distinct().toList();
        if (codes.size() != 1) {
            throw new NexusException(
                    ErrorCode.FORM_SUBMIT_INVALID,
                    400,
                    "G18 submit requires exactly one ledger_code on the running form",
                    Map.of("ledger_codes", codes));
        }
        return codes.getFirst();
    }

    static Map<String, Object> ledgerValues(FormSnapshot snapshot, Map<String, Object> payload) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (FormBinding binding : snapshot.bindings()) {
            if (payload.containsKey(binding.fieldCode())) {
                row.put(binding.fieldCode(), payload.get(binding.fieldCode()));
            }
        }
        return row;
    }

    private FormRecord load(TenantContext tenant, UUID formId) {
        UUID tenantId = UUID.fromString(tenant.tenantId());
        FormRecord form = store.find(tenantId, formId)
                .orElseThrow(() -> new NexusException(ErrorCode.FORM_NOT_FOUND, 404, "form not found"));
        SideEffectGuard.assertSameTenant(tenant, form.tenantId().toString());
        return form;
    }

    private static TenantContext requireTenant() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required");
        }
        return tenant;
    }
}
