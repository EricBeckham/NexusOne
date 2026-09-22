package one.nexus.isolation.api;

import java.time.Instant;
import java.util.Optional;

/** Server-issued access context. Never constructed from a client-only tenant id. */
public sealed interface AccessContext permits TenantContext, PlatformContext {
    Actor actor();
    String traceId();
    Instant issuedAt();

    default Optional<String> boundTenantId() {
        return this instanceof TenantContext tenant ? Optional.of(tenant.tenantId()) : Optional.empty();
    }

    default boolean platform() {
        return this instanceof PlatformContext;
    }
}
