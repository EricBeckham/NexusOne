package one.nexus.p3.studio.meta;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory M0-06 double for tests without JDBC. Production uses {@link JdbcLedgerMetadataPort}.
 */
public final class InMemoryLedgerMetadataCatalog implements LedgerMetadataPort {
    private static final UUID GLOBAL = new UUID(0, 0);

    private final Map<String, LedgerRef> ledgers = new ConcurrentHashMap<>();
    private final Map<String, FieldRef> fields = new ConcurrentHashMap<>();

    public void putLedger(LedgerRef ledger) {
        UUID tenant = ledger.tenantId() == null ? GLOBAL : ledger.tenantId();
        ledgers.put(key(tenant, ledger.ledgerCode()), new LedgerRef(
                tenant, ledger.appCode(), ledger.ledgerCode(), ledger.name(), ledger.usable()));
    }

    public void putField(UUID tenantId, FieldRef field) {
        UUID tenant = tenantId == null ? GLOBAL : tenantId;
        fields.put(fieldKey(tenant, field.ledgerCode(), field.fieldCode()), field);
    }

    public void seedDemoLeaveRequest() {
        putLedger(new LedgerRef(GLOBAL, "demo", "leave_request", "请假台账", true));
        putField(GLOBAL, new FieldRef("leave_request", "title", "标题", "TEXT", true));
        putField(GLOBAL, new FieldRef("leave_request", "days", "天数", "NUMBER", true));
        putField(GLOBAL, new FieldRef("leave_request", "reason", "原因", "TEXT", false));
        putField(GLOBAL, new FieldRef("leave_request", "applicant", "申请人", "PERSON", true));
    }

    public Collection<LedgerRef> ledgers() {
        return ledgers.values();
    }

    @Override
    public Optional<LedgerRef> findLedger(UUID tenantId, String ledgerCode) {
        LedgerRef tenant = ledgers.get(key(tenantId, ledgerCode));
        if (tenant != null) {
            return Optional.of(tenant);
        }
        return Optional.ofNullable(ledgers.get(key(GLOBAL, ledgerCode)));
    }

    @Override
    public Optional<FieldRef> findField(UUID tenantId, String ledgerCode, String fieldCode) {
        FieldRef tenant = fields.get(fieldKey(tenantId, ledgerCode, fieldCode));
        if (tenant != null) {
            return Optional.of(tenant);
        }
        return Optional.ofNullable(fields.get(fieldKey(GLOBAL, ledgerCode, fieldCode)));
    }

    private static String key(UUID tenantId, String ledgerCode) {
        return tenantId + ":" + ledgerCode;
    }

    private static String fieldKey(UUID tenantId, String ledgerCode, String fieldCode) {
        return tenantId + ":" + ledgerCode + ":" + fieldCode;
    }
}
