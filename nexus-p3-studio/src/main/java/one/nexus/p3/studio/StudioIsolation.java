package one.nexus.p3.studio;

import java.time.Clock;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;

/** Issues isolation-kernel TenantContext. Never constructs TenantContext directly. */
public final class StudioIsolation {
    private StudioIsolation() {}

    public static TenantContext bindTenant(
            IsolationKernel kernel, String tenantId, String actorId, String membershipId, String traceId) {
        TenantContext context = kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person(actorId), tenantId, membershipId, Set.of(tenantId)),
                traceId);
        AccessContextHolder.set(context);
        return context;
    }

    public static void clear() {
        AccessContextHolder.clear();
    }
}
