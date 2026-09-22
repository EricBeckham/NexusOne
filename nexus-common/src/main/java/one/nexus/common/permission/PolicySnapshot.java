package one.nexus.common.permission;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned permission snapshot handle. Redis invalidation is M0-05 / M0-10.
 */
public record PolicySnapshot(UUID tenantId, long version, Instant generatedAt) {
    public PolicySnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        Objects.requireNonNull(generatedAt, "generatedAt");
    }
}
