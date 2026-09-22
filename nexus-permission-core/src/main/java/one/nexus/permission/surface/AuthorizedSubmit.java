package one.nexus.permission.surface;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.Decision;

/**
 * Job / Task submit authorization. Calls {@link PermissionService#decide}
 * through {@link SurfaceGuard} before IsolatedExport or enqueue/schedule.
 * Isolation-only jobs with no P2 {@code TenantContext} skip this engine
 * (same leftover as list/detail tenant-only).
 */
public final class AuthorizedSubmit {
    private AuthorizedSubmit() {}

    public static Decision task(PermissionService service, String taskType) {
        if (TenantContextHolder.get() == null) {
            return null;
        }
        return SurfaceCatalog.requireTask(service, taskType);
    }

    public static Decision job(PermissionService service, String payloadType) {
        if (TenantContextHolder.get() == null) {
            return null;
        }
        return SurfaceCatalog.requireJob(service, payloadType);
    }

    public static Decision export(PermissionService service) {
        if (TenantContextHolder.get() == null) {
            return null;
        }
        return SurfaceCatalog.requireExport(service);
    }
}
