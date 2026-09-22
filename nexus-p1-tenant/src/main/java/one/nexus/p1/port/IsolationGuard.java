package one.nexus.p1.port;

import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;

/**
 * M0-02 contract. P1 calls this; it does not implement Query AST / TableAccessPlan Guard.
 *
 * <p>When M0-02 lands, replace {@code one.nexus.p1.infra.isolation} with the kernel types
 * (or have the kernel implement this interface). Tenant lifecycle APIs are platform-scope
 * whitelist paths: they must run inside an explicit PlatformContext and be audited.
 */
public interface IsolationGuard {

    PlatformContext requirePlatformContext(String operation, PlatformDuty duty);

    /**
     * Quota consume/release from P3 may run in the tenant's own context or in PlatformContext.
     */
    void requireTenantOrPlatform(UUID tenantId, String operation);

    IsolationSnapshot current();

    record IsolationSnapshot(Actor actor, PlatformContext platform, UUID tenantId, String traceId) {
        public UUID actorId() {
            return actor == null ? null : actor.subjectId();
        }
    }
}
