package one.nexus.p3.infra.jdbc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.domain.SystemColumns;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.port.LedgerRepository;

/**
 * G18 sink: LedgerRecord writes through GuardedLedgerStore (Query AST). No DDL.
 */
@Component
@ConditionalOnBean(GuardedLedgerStore.class)
public class SchemaLedgerRecordSink implements LedgerRecordSink {
    private final LedgerRepository ledgers;
    private final FieldRepository fields;
    private final GuardedLedgerStore store;

    public SchemaLedgerRecordSink(LedgerRepository ledgers, FieldRepository fields, GuardedLedgerStore store) {
        this.ledgers = ledgers;
        this.fields = fields;
        this.store = store;
    }

    @Override
    public UUID insert(String ledgerCode, Map<String, Object> values) {
        LedgerDefinition ledger = readyByCode(ledgerCode);
        List<LedgerField> fieldList = fields.listByLedger(ledger.tenantId(), ledger.id());
        Map<String, Object> row = withSystemDefaults(values == null ? Map.of() : values);
        maybeStatus(row, PENDING, fieldList);
        return store.insert(ledger, fieldList, row);
    }

    @Override
    public Map<String, Object> update(UUID ledgerId, UUID recordId, Map<String, Object> values) {
        LedgerDefinition ledger = readyById(ledgerId);
        List<LedgerField> fieldList = fields.listByLedger(ledger.tenantId(), ledger.id());
        return store.update(ledger, fieldList, recordId, values == null ? Map.of() : values, null);
    }

    @Override
    public Map<String, Object> get(UUID ledgerId, UUID recordId) {
        LedgerDefinition ledger = readyById(ledgerId);
        List<LedgerField> fieldList = fields.listByLedger(ledger.tenantId(), ledger.id());
        return store.detail(ledger, fieldList, recordId);
    }

    @Override
    public Optional<UUID> findLedgerId(UUID tenantId, String ledgerCode) {
        return ledgers.findByCode(tenantId, ledgerCode).map(LedgerDefinition::id);
    }

    @Override
    public Optional<String> physicalTable(UUID tenantId, UUID ledgerId) {
        return ledgers.findById(tenantId, ledgerId).map(LedgerDefinition::physicalTable);
    }

    @Override
    public void writeApprovalStatus(UUID ledgerId, UUID recordId, String status) {
        LedgerDefinition ledger = readyById(ledgerId);
        List<LedgerField> fieldList = fields.listByLedger(ledger.tenantId(), ledger.id());
        if (fieldList.stream().noneMatch(field -> APPROVAL_STATUS.equals(field.fieldCode()))) {
            return;
        }
        store.update(ledger, fieldList, recordId, Map.of(APPROVAL_STATUS, status), null);
    }

    private LedgerDefinition readyByCode(String ledgerCode) {
        UUID tenantId = tenantId();
        LedgerDefinition ledger = ledgers.findByCode(tenantId, ledgerCode)
                .orElseThrow(() -> P3Exception.ledgerNotFound(ledgerCode));
        if (!ledger.ready()) {
            throw P3Exception.ledgerNotReady(ledger.id());
        }
        return ledger;
    }

    private LedgerDefinition readyById(UUID ledgerId) {
        UUID tenantId = tenantId();
        LedgerDefinition ledger = ledgers.findById(tenantId, ledgerId)
                .orElseThrow(() -> P3Exception.ledgerNotFound(ledgerId));
        if (!ledger.ready()) {
            throw P3Exception.ledgerNotReady(ledger.id());
        }
        return ledger;
    }

    private static UUID tenantId() {
        String bound = AccessContextHolder.current().boundTenantId().orElseThrow(P3Exception::missingTenant);
        return UUID.fromString(bound);
    }

    private static Map<String, Object> withSystemDefaults(Map<String, Object> values) {
        Map<String, Object> row = new LinkedHashMap<>(values);
        String actor = AccessContextHolder.current().actor().actorId();
        row.putIfAbsent(SystemColumns.OWNER_ID, actor);
        return row;
    }

    private static void maybeStatus(Map<String, Object> row, String status, List<LedgerField> fieldList) {
        if (fieldList.stream().anyMatch(field -> APPROVAL_STATUS.equals(field.fieldCode()))
                && !row.containsKey(APPROVAL_STATUS)) {
            row.put(APPROVAL_STATUS, status);
        }
    }
}
