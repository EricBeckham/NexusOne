package one.nexus.common.context;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-issued tenant scope for a request, job, or cache key.
 *
 * <p>M0-02 owns issuance and propagation. Clients must not supply this as a
 * trusted header. Platform-wide work uses {@link PlatformContext} instead.
 */
public record TenantContext(UUID tenantId, String tenantCode) {
    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        if (!tenantCode.matches("[a-z][a-z0-9_]{0,23}")) {
            throw new IllegalArgumentException("tenantCode must match [a-z][a-z0-9_]{0,23}");
        }
    }
}
