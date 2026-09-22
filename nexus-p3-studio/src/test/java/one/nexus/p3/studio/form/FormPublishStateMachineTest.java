package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.studio.StudioIsolation;
import one.nexus.p3.studio.meta.FieldRef;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;
import one.nexus.p3.studio.meta.LedgerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormPublishStateMachineTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private final IsolationKernel kernel =
            IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
    private InMemoryLedgerMetadataCatalog metadata;
    private InMemoryFormStore store;

    @BeforeEach
    void bind() {
        metadata = new InMemoryLedgerMetadataCatalog();
        metadata.seedDemoLeaveRequest();
        store = new InMemoryFormStore();
        StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-a", "trace-form");
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void draftValidatePublishRunningAndFill() {
        FormService service = service(PublishHook.noop());
        FormRecord created = service.create("demo", "leave_form", "请假单", null, leaveBindings());
        assertEquals(FormStatus.DRAFT, created.draftStatus());
        assertNull(created.runningVersion());

        NexusException tooSoon = assertThrows(NexusException.class, () -> service.publish(created.formId(), "v1"));
        assertEquals(ErrorCode.FORM_INVALID_STATE, tooSoon.errorCode());

        FormRecord validated = service.validate(created.formId());
        assertEquals(FormStatus.VALIDATED, validated.draftStatus());

        FormRecord published = service.publish(created.formId(), "first");
        assertEquals(1, published.runningVersion());
        assertEquals(FormStatus.DRAFT, published.draftStatus());
        assertEquals(VersionStatus.RUNNING, service.running(created.formId()).status());

        FormSubmission submission = service.submit(created.formId(), Map.of(
                "title", "年假",
                "days", 3,
                "applicant", "alice"));
        assertEquals(1, submission.runningVersion());
        assertEquals("年假", submission.values().get("title"));
        assertNull(submission.ledgerRecordId());
    }

    @Test
    void submitWritesThroughLedgerRecordSink() {
        UUID ledgerId = UUID.fromString("0199a1c0-0000-7000-8000-00000000led1");
        UUID recordId = UUID.fromString("0199a1c0-0000-7000-8000-00000000rec1");
        AtomicReference<String> code = new AtomicReference<>();
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        LedgerRecordSink sink = new LedgerRecordSink() {
            @Override
            public UUID insert(String ledgerCode, Map<String, Object> values) {
                code.set(ledgerCode);
                captured.set(values);
                return recordId;
            }

            @Override
            public Map<String, Object> update(UUID id, UUID rec, Map<String, Object> values) {
                return Map.of();
            }

            @Override
            public Map<String, Object> get(UUID id, UUID rec) {
                return Map.of();
            }

            @Override
            public java.util.Optional<UUID> findLedgerId(UUID tenantId, String ledgerCode) {
                return java.util.Optional.of(ledgerId);
            }

            @Override
            public java.util.Optional<String> physicalTable(UUID tenantId, UUID id) {
                return java.util.Optional.of("l_alpha_leave");
            }

            @Override
            public void writeApprovalStatus(UUID id, UUID rec, String status) {}
        };
        FormService service = new FormService(store, metadata, PublishHook.noop(), Clock.systemUTC(), null, sink);
        FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
        service.validate(form.formId());
        service.publish(form.formId(), "v1");
        FormSubmission submission = service.submit(form.formId(), Map.of(
                "title", "年假", "days", 3, "applicant", "alice"));
        assertEquals(recordId, submission.ledgerRecordId());
        assertEquals(ledgerId, submission.ledgerId());
        assertEquals("leave_request", code.get());
        assertEquals("年假", captured.get().get("title"));
    }

    @Test
    void publishFailureDoesNotChangeRunningVersion() {
        AtomicInteger calls = new AtomicInteger();
        PublishHook hook = snapshot -> {
            if (calls.incrementAndGet() >= 2) {
                throw new IllegalStateException("schema drift");
            }
        };
        FormService service = service(hook);
        FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
        service.validate(form.formId());
        service.publish(form.formId(), "v1");
        assertEquals(1, service.get(form.formId()).runningVersion());

        service.update(form.formId(), "请假单 v2", null, leaveBindings());
        service.validate(form.formId());
        NexusException failed = assertThrows(NexusException.class, () -> service.publish(form.formId(), "v2"));
        assertEquals(ErrorCode.FORM_PUBLISH_FAILED, failed.errorCode());
        assertEquals(1, service.get(form.formId()).runningVersion());
        assertEquals(1, service.versions(form.formId()).size());
        assertEquals(VersionStatus.RUNNING, service.running(form.formId()).status());
        assertTrue(service.publishLog(form.formId()).stream().anyMatch(log -> "ROLLED_BACK".equals(log.outcome())));
        assertEquals(1, service.running(form.formId()).snapshot().versionNo());
    }

    @Test
    void validateFailureLeavesDraftAndDoesNotCreateRunning() {
        FormService service = service(PublishHook.noop());
        FormRecord form = service.create(
                "demo",
                "leave_form",
                "请假单",
                null,
                List.of(new FormBinding("w1", "missing_ledger", "title", true, false, WidgetType.TEXT, 0)));
        NexusException failed = assertThrows(NexusException.class, () -> service.validate(form.formId()));
        assertEquals(ErrorCode.FORM_VALIDATE_FAILED, failed.errorCode());
        assertEquals(FormStatus.DRAFT, service.get(form.formId()).draftStatus());
        assertNull(service.get(form.formId()).runningVersion());
        assertTrue(service.versions(form.formId()).isEmpty());
    }

    @Test
    void tenantBCannotSeeTenantAForm() {
        FormService service = service(PublishHook.noop());
        FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
        AccessContextHolder.clear();
        StudioIsolation.bindTenant(
                kernel, "22222222-2222-4222-8222-222222222222", "designer-b", "mem-b", "trace-b");
        NexusException missing = assertThrows(NexusException.class, () -> service.get(form.formId()));
        assertEquals(ErrorCode.FORM_NOT_FOUND, missing.errorCode());
        assertTrue(service.list().isEmpty());
    }

    @Test
    void tenantSpecificLedgerIsNotVisibleGloballyUntilSeeded() {
        UUID tenant = UUID.fromString(TENANT);
        metadata.putLedger(new LedgerRef(tenant, "hr", "expense", "报销", true));
        metadata.putField(tenant, new FieldRef("expense", "amount", "金额", "NUMBER", true));
        FormService service = service(PublishHook.noop());
        FormRecord ok = service.create(
                "hr",
                "expense_form",
                "报销单",
                null,
                List.of(new FormBinding("amount", "expense", "amount", true, false, WidgetType.NUMBER, 0)));
        assertEquals(FormStatus.VALIDATED, service.validate(ok.formId()).draftStatus());
    }

    private FormService service(PublishHook hook) {
        return new FormService(store, metadata, hook, Clock.systemUTC(), null);
    }

    private static List<FormBinding> leaveBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("reason", "leave_request", "reason", false, false, WidgetType.TEXTAREA, 2),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 3));
    }
}
