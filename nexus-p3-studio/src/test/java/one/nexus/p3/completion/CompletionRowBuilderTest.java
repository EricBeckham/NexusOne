package one.nexus.p3.completion;

import static one.nexus.p3.completion.CompletionTestLedgers.demoMapping;
import static one.nexus.p3.completion.CompletionTestLedgers.expenseFields;
import static one.nexus.p3.completion.CompletionTestLedgers.ledger;
import static one.nexus.p3.completion.CompletionTestLedgers.paymentFields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.completion.CompletionAction.FieldMapping;
import one.nexus.p3.completion.CompletionAction.SourceKind;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerStatus;
import org.junit.jupiter.api.Test;

class CompletionRowBuilderTest {
    final UUID tenant = UUID.randomUUID();
    final LedgerDefinition expense = ledger(tenant, "expense", LedgerStatus.READY);
    final LedgerDefinition payment = ledger(tenant, "payment", LedgerStatus.READY);

    List<String> problems(List<FieldMapping> mapping) {
        return CompletionRowBuilder.validate(mapping, expenseFields(expense), paymentFields(payment));
    }

    @Test
    void theDemoMappingIsValid() {
        assertEquals(List.of(), problems(demoMapping()));
    }

    @Test
    void constantsMustParseAsTheTargetType() {
        assertTrue(CompletionRowBuilder.parses(FieldType.NUMERIC, "12.5"));
        assertFalse(CompletionRowBuilder.parses(FieldType.NUMERIC, "twelve"));
        assertTrue(CompletionRowBuilder.parses(FieldType.BOOLEAN, "TRUE"));
        assertFalse(CompletionRowBuilder.parses(FieldType.BOOLEAN, "yes"));
        assertTrue(CompletionRowBuilder.parses(FieldType.TIMESTAMPTZ, "2026-09-27T00:00:00Z"));
        assertFalse(CompletionRowBuilder.parses(FieldType.INTEGER, "1.5"));
    }

    @Test
    void typeCompatibility() {
        assertTrue(CompletionRowBuilder.compatible(FieldType.NUMERIC, FieldType.TEXT));
        assertTrue(CompletionRowBuilder.compatible(FieldType.INTEGER, FieldType.NUMERIC));
        assertFalse(CompletionRowBuilder.compatible(FieldType.TEXT, FieldType.NUMERIC));
        assertFalse(CompletionRowBuilder.compatible(FieldType.NUMERIC, FieldType.INTEGER));
    }

    @Test
    void reportsEveryProblemAtOnce() {
        List<FieldMapping> mapping = List.of(
                new FieldMapping("payee", SourceKind.SYSTEM, "NOT_A_VALUE"),
                new FieldMapping("amount", SourceKind.CONST, "abc"),
                new FieldMapping("memo", SourceKind.FIELD, "missing_field"),
                new FieldMapping("memo", SourceKind.CONST, "dup"),
                new FieldMapping("decided_at", SourceKind.SYSTEM, "INITIATOR"),
                new FieldMapping("id", SourceKind.CONST, "x"));
        List<String> problems = problems(mapping);
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("payee")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("amount")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("missing_field")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("mapped twice")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("decided_at")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("id:")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("pay_status") && p.contains("required")),
                problems.toString());
    }

    @Test
    void buildsTheRowFromFieldsConstantsAndSystemValues() {
        UUID record = UUID.randomUUID();
        Instant done = Instant.parse("2026-09-27T08:00:00Z");
        Map<String, Object> row = CompletionRowBuilder.build(demoMapping(),
                Map.of("amount", new java.math.BigDecimal("99.00"), "reason", "会议"),
                new CompletionRowBuilder.Completion(record, "zhou", "lin", done, "APPROVED"));
        assertEquals("zhou", row.get("payee"));
        assertEquals(new java.math.BigDecimal("99.00"), row.get("amount"));
        assertEquals("会议", row.get("memo"));
        assertEquals(record.toString(), row.get("source_no"));
        assertEquals("待付款", row.get("pay_status"));
        assertEquals("2026-09-27T08:00:00Z", row.get("decided_at"));
    }

    @Test
    void sourceTimestampsBecomeIsoStrings() {
        Instant at = Instant.parse("2026-01-02T03:04:05Z");
        Map<String, Object> row = CompletionRowBuilder.build(
                List.of(new FieldMapping("decided_at", SourceKind.FIELD, "at")),
                Map.of("at", Timestamp.from(at)), null);
        assertEquals("2026-01-02T03:04:05Z", row.get("decided_at"));
    }
}
