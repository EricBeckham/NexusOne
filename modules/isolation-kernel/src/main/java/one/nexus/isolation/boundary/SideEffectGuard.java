package one.nexus.isolation.boundary;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditorHolder;

/** Rejects cross-tenant payloads before cache/file/outbox/async side effects. */
public final class SideEffectGuard {
    private SideEffectGuard() {}

    public static void assertSameTenant(AccessContext current, String resourceTenantId) {
        if (current == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "side-effect missing context");
        }
        if (resourceTenantId == null || resourceTenantId.isBlank()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, current, "side-effect missing resource tenant");
        }
        if (current instanceof PlatformContext) {
            return;
        }
        if (!(current instanceof TenantContext tenant)) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.MISSING_CONTEXT, current, "side-effect requires tenant context");
        }
        if (!tenant.tenantId().equals(resourceTenantId)) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_ASYNC, current, "async payload tenant mismatch");
        }
    }
}
