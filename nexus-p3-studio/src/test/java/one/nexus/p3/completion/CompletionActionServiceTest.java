package one.nexus.p3.completion;

import static one.nexus.p3.completion.CompletionTestLedgers.demoMapping;
import static one.nexus.p3.completion.CompletionTestLedgers.expenseFields;
import static one.nexus.p3.completion.CompletionTestLedgers.field;
import static one.nexus.p3.completion.CompletionTestLedgers.ledger;
import static one.nexus.p3.completion.CompletionTestLedgers.paymentFields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.completion.CompletionAction.FieldMapping;
import one.nexus.p3.completion.CompletionAction.SourceKind;
import one.nexus.p3.completion.CompletionAction.Trigger;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.port.LedgerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CompletionActionServiceTest {
    final UUID tenant = UUID.randomUUID();
    final LedgerDefinition expense = ledger(tenant, "expense", LedgerStatus.READY);
    final LedgerDefinition payment = ledger(tenant, "payment", LedgerStatus.READY);
    final LedgerDefinition draftLedger = ledger(tenant, "draft_ledger", LedgerStatus.DRAFT);
    final CompletionTestLedgers.MemoryStore store = new CompletionTestLedgers.MemoryStore();
    final LedgerRepository ledgers = mock(LedgerRepository.class);
    final FieldRepository fields = mock(FieldRepository.class);
    final LedgerRecordSink sink = mock(LedgerRecordSink.class);
    CompletionActionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        AccessContextHolder.set(new TenantContext(tenant.toString(), Actor.person("lin"), "membership-1", "trace-1",
                Instant.now()));
        for (LedgerDefinition item : List.of(expense, payment, draftLedger)) {
            when(ledgers.findByCode(tenant, item.ledgerCode())).thenReturn(Optional.of(item));
        }
        when(fields.listByLedger(tenant, expense.id())).thenReturn(expenseFields(expense));
        when(fields.listByLedger(tenant, payment.id())).thenReturn(paymentFields(payment));
        ObjectProvider<LedgerRecordSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);
        service = new CompletionActionService(store, ledgers, fields, provider);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    CompletionActionService.Draft draft(String target, List<FieldMapping> mapping) {
        return new CompletionActionService.Draft("报销通过→付款", "expense", "expenseApproval", Trigger.APPROVED,
                target, mapping, null);
    }

    static String code(Runnable call) {
        return assertThrows(P3Exception.class, call::run).errorCode().code();
    }

    @Test
    void createsAValidActionScopedToTheBoundTenantAndActor() {
        CompletionAction action = service.create(draft("payment", demoMapping()));
        assertEquals(tenant, action.tenantId());
        assertEquals("lin", action.createdBy());
        assertEquals(1, action.version());
        assertTrue(action.enabled());
        assertEquals(List.of(action), service.list());
    }

    @Test
    void refusesInvalidConfigurations() {
        assertEquals("P3.ACTION.0001", code(() -> service.create(draft("expense", demoMapping()))));
        assertEquals("P3.ACTION.0001", code(() -> service.create(draft("missing", demoMapping()))));
        assertEquals("P3.ACTION.0001", code(() -> service.create(draft("draft_ledger", demoMapping()))));
        // required target field pay_status left out
        assertEquals("P3.ACTION.0001", code(() -> service.create(draft("payment", demoMapping().subList(0, 4)))));
        // NUMERIC target filled from a TEXT source field
        List<FieldMapping> wrongType = new java.util.ArrayList<>(demoMapping());
        wrongType.set(1, new FieldMapping("amount", SourceKind.FIELD, "title"));
        P3Exception error = assertThrows(P3Exception.class, () -> service.create(draft("payment", wrongType)));
        assertTrue(String.valueOf(error.details().get("problems")).contains("amount"));
    }

    @Test
    void updatesWithOptimisticVersionAndCanDisable() {
        CompletionAction created = service.create(draft("payment", demoMapping()));
        CompletionActionService.Draft disable = new CompletionActionService.Draft("报销通过→付款", "expense",
                "expenseApproval", Trigger.ANY, "payment", demoMapping(), false);
        CompletionAction updated = service.update(created.id(), 1, disable);
        assertEquals(2, updated.version());
        assertEquals(Trigger.ANY, updated.trigger());
        assertEquals(false, updated.enabled());
        assertEquals("P3.ACTION.0003", code(() -> service.update(created.id(), 1, disable)));
        assertEquals("P3.ACTION.0002", code(() -> service.update(UUID.randomUUID(), 1, disable)));
    }

    @Test
    void anotherTenantCannotSeeOrChangeTheAction() {
        CompletionAction created = service.create(draft("payment", demoMapping()));
        AccessContextHolder.set(new TenantContext(UUID.randomUUID().toString(), Actor.person("x"), "m", "t",
                Instant.now()));
        assertTrue(service.list().isEmpty());
        assertEquals("P3.ACTION.0002", code(() -> service.get(created.id())));
    }

    @Test
    void previewBuildsTheRowWithoutWriting() {
        CompletionAction created = service.create(draft("payment", demoMapping()));
        UUID record = UUID.randomUUID();
        when(sink.get(expense.id(), record)).thenReturn(Map.of("title", "差旅", "amount", "128.50",
                "reason", "北京出差", "owner_id", "zhou"));

        Map<String, Object> row = service.preview(created.id(), record, null);

        assertEquals("zhou", row.get("payee"));
        assertEquals("128.50", row.get("amount"));
        assertEquals("北京出差", row.get("memo"));
        assertEquals(record.toString(), row.get("source_no"));
        assertEquals("待付款", row.get("pay_status"));
        verify(sink, never()).insert(any(), any());
    }

    @Test
    void theMappingCannotTouchSystemColumnsOrUnknownFields() {
        List<FieldMapping> bad = new java.util.ArrayList<>(demoMapping());
        bad.add(new FieldMapping("created_by", SourceKind.CONST, "someone"));
        bad.add(new FieldMapping("nope", SourceKind.CONST, "x"));
        P3Exception error = assertThrows(P3Exception.class, () -> service.create(draft("payment", bad)));
        String problems = String.valueOf(error.details().get("problems"));
        assertTrue(problems.contains("created_by") && problems.contains("nope"), problems);
        field(expense, "x", FieldType.TEXT, false); // fixture sanity
    }
}
