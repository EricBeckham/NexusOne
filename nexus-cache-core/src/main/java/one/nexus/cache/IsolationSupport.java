package one.nexus.cache;

import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;

/** Issues and binds isolation-kernel context. Never constructs TenantContext directly. */
public final class IsolationSupport {
    private IsolationSupport() {}

    public static TenantContext bindTenant(
            IsolationKernel kernel, String tenantId, String actorId, String membershipId, String traceId) {
        TenantContext context = kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person(actorId), tenantId, membershipId, Set.of(tenantId)),
                traceId);
        AccessContextHolder.set(context);
        return context;
    }

    public static PlatformContext bindPlatform(
            IsolationKernel kernel, String path, String actorId, String reason, String traceId) {
        PlatformContext context = kernel.platform().issue(path, Actor.platformAdmin(actorId), reason, traceId);
        AccessContextHolder.set(context);
        return context;
    }

    public static void clear() {
        AccessContextHolder.clear();
    }
}
