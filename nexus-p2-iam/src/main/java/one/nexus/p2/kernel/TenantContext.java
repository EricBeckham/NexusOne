package one.nexus.p2.kernel;

import java.time.Instant;
import java.util.UUID;

/**
 * Server-issued tenant context consumed by M0-02 TenantContext.
 * Never constructed from a client-supplied tenant_id header.
 */
public record TenantContext(
        UUID contextId,
        UUID tenantId,
        UUID accountId,
        UUID personId,
        String idpSubject,
        Instant issuedAt,
        Instant expiresAt
) {
    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
