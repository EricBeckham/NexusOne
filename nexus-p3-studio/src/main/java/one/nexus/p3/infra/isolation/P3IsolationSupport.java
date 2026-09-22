package one.nexus.p3.infra.isolation;

import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.domain.LedgerIdentifiers;

/** Issues isolation-kernel TenantContext. Never constructs TenantContext directly. */
public final class P3IsolationSupport {
    private P3IsolationSupport() {}

    public static TenantContext bind(
            IsolationKernel kernel, UUID tenantId, String tenantCode, String actorId, String traceId) {
        String tenant = tenantId.toString();
        TenantContext context = kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person(actorId), tenant, "mem-" + tenant, Set.of(tenant)),
                traceId);
        AccessContextHolder.set(context);
        P3RequestContext.set(new P3RequestContext.Snapshot(
                tenantId, LedgerIdentifiers.requireCode(tenantCode), actorId, traceId));
        bindOptionalProductHolders(tenantId, actorId, traceId);
        return context;
    }

    public static void clear() {
        AccessContextHolder.clear();
        P3RequestContext.clear();
        clearOptionalProductHolders();
    }

    private static void bindOptionalProductHolders(UUID tenantId, String actorId, String traceId) {
        try {
            Class<?> holder = Class.forName("one.nexus.p1.infra.isolation.IsolationContextHolder");
            Class<?> snapshot = Class.forName("one.nexus.p1.port.IsolationGuard$IsolationSnapshot");
            Class<?> actorClass = Class.forName("one.nexus.p1.domain.Actor");
            Class<?> platform = Class.forName("one.nexus.p1.port.PlatformContext");
            Object actor = actorClass.getConstructor(UUID.class, UUID.class, String.class, Set.class)
                    .newInstance(tryUuid(actorId), tenantId, "p3-actor", Set.of());
            Object platformCtx = platform.getMethod("inactive").invoke(null);
            Object snap = snapshot.getConstructor(actorClass, platform, UUID.class, String.class)
                    .newInstance(actor, platformCtx, tenantId, traceId);
            holder.getMethod("set", snapshot).invoke(null, snap);
        } catch (ReflectiveOperationException ignored) {
            // P1 optional
        }
        try {
            Class<?> p2Holder = Class.forName("one.nexus.p2.kernel.TenantContextHolder");
            Class<?> p2Ctx = Class.forName("one.nexus.p2.kernel.TenantContext");
            UUID actor = tryUuid(actorId);
            Object context = p2Ctx.getConstructor(
                    UUID.class, UUID.class, UUID.class, UUID.class, String.class,
                    java.time.Instant.class, java.time.Instant.class)
                    .newInstance(
                            one.nexus.common.id.UuidV7.generate(),
                            tenantId,
                            actor,
                            actor,
                            actorId,
                            java.time.Instant.now(),
                            java.time.Instant.now().plusSeconds(3600));
            p2Holder.getMethod("set", p2Ctx).invoke(null, context);
        } catch (ReflectiveOperationException ignored) {
            // P2 optional
        }
    }

    private static void clearOptionalProductHolders() {
        try {
            Class.forName("one.nexus.p1.infra.isolation.IsolationContextHolder")
                    .getMethod("clear").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // P1 optional
        }
        try {
            Class.forName("one.nexus.p2.kernel.TenantContextHolder")
                    .getMethod("clear").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // P2 optional
        }
    }

    private static UUID tryUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            return one.nexus.common.id.UuidV7.generate();
        }
    }
}
