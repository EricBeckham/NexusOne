package one.nexus.p3.completion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;

/** Test fixtures for FA3 / FA4: an expense ledger (source) and a payment ledger (target). */
final class CompletionTestLedgers {
    private CompletionTestLedgers() {}

    static LedgerDefinition ledger(UUID tenant, String code, LedgerStatus status) {
        Instant now = Instant.now();
        return new LedgerDefinition(UUID.nameUUIDFromBytes((tenant + code).getBytes()), tenant, "acme", null, code,
                code, LedgerScope.TENANT, "t_" + code, 1, status, now, "test", now, "test", null, 1);
    }

    static LedgerField field(LedgerDefinition ledger, String code, FieldType type, boolean required) {
        Instant now = Instant.now();
        return new LedgerField(UUID.randomUUID(), ledger.tenantId(), ledger.id(), code, code, type, required, false, 0,
                now, "test", now, "test", null, 1);
    }

    static List<LedgerField> expenseFields(LedgerDefinition expense) {
        return List.of(
                field(expense, "title", FieldType.TEXT, true),
                field(expense, "amount", FieldType.NUMERIC, true),
                field(expense, "reason", FieldType.TEXT, false),
                field(expense, "approval_status", FieldType.TEXT, false));
    }

    static List<LedgerField> paymentFields(LedgerDefinition payment) {
        return List.of(
                field(payment, "payee", FieldType.TEXT, true),
                field(payment, "amount", FieldType.NUMERIC, true),
                field(payment, "memo", FieldType.TEXT, false),
                field(payment, "source_no", FieldType.TEXT, false),
                field(payment, "pay_status", FieldType.TEXT, true),
                field(payment, "decided_at", FieldType.TIMESTAMPTZ, false));
    }

    /** The FA1 §9 demo mapping: expense approved → payment row "待付款". */
    static List<CompletionAction.FieldMapping> demoMapping() {
        return List.of(
                new CompletionAction.FieldMapping("payee", CompletionAction.SourceKind.SYSTEM, "INITIATOR"),
                new CompletionAction.FieldMapping("amount", CompletionAction.SourceKind.FIELD, "amount"),
                new CompletionAction.FieldMapping("memo", CompletionAction.SourceKind.FIELD, "reason"),
                new CompletionAction.FieldMapping("source_no", CompletionAction.SourceKind.SYSTEM, "SOURCE_RECORD_ID"),
                new CompletionAction.FieldMapping("pay_status", CompletionAction.SourceKind.CONST, "待付款"),
                new CompletionAction.FieldMapping("decided_at", CompletionAction.SourceKind.SYSTEM, "COMPLETED_AT"));
    }

    /** In-memory {@link CompletionActionStore} for unit tests. */
    static final class MemoryStore implements CompletionActionStore {
        final Map<UUID, CompletionAction> rows = new ConcurrentHashMap<>();

        @Override public void insert(CompletionAction action) { rows.put(action.id(), action); }

        @Override
        public boolean update(CompletionAction action, long expectedVersion) {
            CompletionAction current = rows.get(action.id());
            if (current == null || !current.tenantId().equals(action.tenantId()) || current.version() != expectedVersion) {
                return false;
            }
            rows.put(action.id(), action);
            return true;
        }

        @Override
        public Optional<CompletionAction> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(item -> item.tenantId().equals(tenantId));
        }

        @Override
        public List<CompletionAction> list(UUID tenantId) {
            List<CompletionAction> out = new ArrayList<>(rows.values().stream()
                    .filter(item -> item.tenantId().equals(tenantId)).toList());
            out.sort(Comparator.comparing(CompletionAction::createdAt));
            return out;
        }

        @Override
        public List<CompletionAction> enabledFor(UUID tenantId, String sourceLedgerCode, String processKey) {
            return list(tenantId).stream()
                    .filter(item -> item.enabled() && item.sourceLedgerCode().equals(sourceLedgerCode)
                            && item.processKey().equals(processKey))
                    .toList();
        }
    }
}
