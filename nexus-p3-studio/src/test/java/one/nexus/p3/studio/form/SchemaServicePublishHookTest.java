package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.SchemaAst;
import one.nexus.p3.schema.SchemaService;
import one.nexus.p3.studio.StudioIsolation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaServicePublishHookTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private final IsolationKernel kernel =
            IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());

    @BeforeEach
    void bind() {
        StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-a", "trace-gate");
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void confirmRunsOncePerBoundLedger() {
        AtomicInteger confirms = new AtomicInteger();
        SchemaService schema = new SchemaService() {
            @Override
            public LedgerDefinition createPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
                return ledger;
            }

            @Override
            public LedgerDefinition evolvePhysicalTable(
                    LedgerDefinition ledger, List<LedgerField> currentFields, LedgerField added) {
                return ledger;
            }

            @Override
            public SchemaAst astOf(LedgerDefinition ledger, List<LedgerField> fields) {
                return new SchemaAst(ledger.physicalTable(), ledger.scope(), List.of());
            }

            @Override
            public void registerRuntime(LedgerDefinition ledger, List<LedgerField> fields) {}

            @Override
            public void confirmPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
                confirms.incrementAndGet();
            }
        };
        UUID tenant = UUID.fromString(TENANT);
        SchemaServicePublishHook hook = new SchemaServicePublishHook(
                schema, FormPublishTestStubs.leaveLedgers(tenant), FormPublishTestStubs.leaveFields(tenant));
        FormSnapshot snapshot = new FormSnapshot(
                UUID.randomUUID(),
                "leave_form",
                "demo",
                "请假单",
                "",
                1,
                List.of(
                        new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                        new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1)));
        hook.beforeActivate(snapshot);
        assertEquals(1, confirms.get());
    }

    @Test
    void schemaServiceAbortPropagates() {
        UUID tenant = UUID.fromString(TENANT);
        SchemaServicePublishHook hook = new SchemaServicePublishHook(
                FormPublishTestStubs.abortOnNthConfirm(1),
                FormPublishTestStubs.leaveLedgers(tenant),
                FormPublishTestStubs.leaveFields(tenant));
        FormSnapshot snapshot = new FormSnapshot(
                UUID.randomUUID(),
                "leave_form",
                "demo",
                "请假单",
                "",
                2,
                List.of(new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0)));
        IllegalStateException failed = assertThrows(IllegalStateException.class, () -> hook.beforeActivate(snapshot));
        assertTrue(failed.getMessage().contains("SchemaService evolve failed"));
        assertTrue(failed.getCause() instanceof java.sql.SQLException);
        assertEquals("42701", ((java.sql.SQLException) failed.getCause()).getSQLState());
    }
}
