package one.nexus.isolation.boundary;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditorHolder;

/**
 * Export-path guard. Cross-tenant export is rejected before Query AST, file,
 * cache, or Job/Task work runs. Uses the same {@code AccessContext} as M0-02.
 */
public final class ExportGuard {
    private ExportGuard() {}

    public static void assertExportTenant(AccessContext current, String resourceTenantId) {
        if (current == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "export missing context");
        }
        if (resourceTenantId == null || resourceTenantId.isBlank()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, current, "export missing resource tenant");
        }
        if (current instanceof PlatformContext) {
            return;
        }
        if (!(current instanceof TenantContext tenant)) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.MISSING_CONTEXT, current, "export requires tenant context");
        }
        if (!tenant.tenantId().equals(resourceTenantId)) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CROSS_TENANT_EXPORT, current, "export tenant mismatch");
        }
    }
}
