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
}
