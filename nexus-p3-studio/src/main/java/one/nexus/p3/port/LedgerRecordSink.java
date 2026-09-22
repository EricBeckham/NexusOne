package one.nexus.p3.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes business rows into the SchemaService-managed physical ledger table
 * using existing {@link one.nexus.p3.infra.jdbc.GuardedLedgerStore} APIs.
 * Forms bind logical codes only; this sink resolves them to a READY ledger.
 */
public interface LedgerRecordSink {
    String APPROVAL_STATUS = "approval_status";
    String PENDING = "PENDING";
    String IN_APPROVAL = "IN_APPROVAL";
    String APPROVED = "APPROVED";
    String REJECTED = "REJECTED";
    String WITHDRAWN = "WITHDRAWN";

    UUID insert(String ledgerCode, Map<String, Object> values);

    Map<String, Object> update(UUID ledgerId, UUID recordId, Map<String, Object> values);

    Map<String, Object> get(UUID ledgerId, UUID recordId);

    Optional<UUID> findLedgerId(UUID tenantId, String ledgerCode);

    Optional<String> physicalTable(UUID tenantId, UUID ledgerId);

    void writeApprovalStatus(UUID ledgerId, UUID recordId, String status);
}
