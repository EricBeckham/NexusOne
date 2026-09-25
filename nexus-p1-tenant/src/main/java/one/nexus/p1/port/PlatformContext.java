package one.nexus.p1.port;

import java.util.UUID;
import one.nexus.p1.domain.Actor;

/**
 * Server-issued cross-tenant access (tech framework §18.5). Never accepted from client headers
 * in production (ARCH-024). Demo filter is gated by {@code nexus.p1.demo-context.enabled}.
 */
public record PlatformContext(
        boolean active,
        String reason,
        Actor actor,
        String traceId,
        UUID actorHomeTenantId
) {

    public static PlatformContext inactive() {
        return new PlatformContext(false, null, null, null, null);
    }

    /**
     * {@code platform.audit_event.tenant_id} is NOT NULL. Platform-scoped writes (form templates,
     * product definitions, …) have no subject tenant, so prefer the actor home tenant and fall
     * back to the configured platform tenant id.
     */
    public UUID auditTenantId(UUID platformTenantId) {
        if (actorHomeTenantId != null) {
            return actorHomeTenantId;
        }
        if (platformTenantId != null) {
            return platformTenantId;
        }
        throw new IllegalStateException("platform audit requires actor home tenant or platform tenant id");
    }
}
