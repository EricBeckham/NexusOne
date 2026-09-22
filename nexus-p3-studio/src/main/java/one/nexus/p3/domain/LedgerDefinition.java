package one.nexus.p3.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerDefinition(
        UUID id,
        UUID tenantId,
        String tenantCode,
        UUID applicationId,
        String ledgerCode,
        String displayName,
        LedgerScope scope,
        String physicalTable,
        int schemaVersion,
        LedgerStatus status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant deletedAt,
        long version
) {
    public boolean ready() {
        return status == LedgerStatus.READY;
    }
}
