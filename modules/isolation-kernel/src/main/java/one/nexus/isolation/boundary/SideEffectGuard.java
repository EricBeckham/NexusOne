package one.nexus.isolation.boundary;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;

/** Rejects cross-tenant payloads before cache/file/outbox/async side effects. */
public final class SideEffectGuard {
    private SideEffectGuard() {}

    public static void assertSameTenant(AccessContext current, String resourceTenantId) {
        if (current == null) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, "missing-trace");
        }
        if (resourceTenantId == null || resourceTenantId.isBlank()) {
            throw new IsolationException(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, current.traceId());
        }
        if (current instanceof PlatformContext) {
            return;
        }
        if (!(current instanceof TenantContext tenant)) {
            throw new IsolationException(IsolationErrorCode.MISSING_CONTEXT, current.traceId());
        }
        if (!tenant.tenantId().equals(resourceTenantId)) {
            throw new IsolationException(IsolationErrorCode.CROSS_TENANT_ASYNC, current.traceId());
        }
    }
}
