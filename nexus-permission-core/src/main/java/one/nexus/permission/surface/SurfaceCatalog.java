package one.nexus.permission.surface;

import one.nexus.audit.AuditPort;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.Decision;

/**
 * Resource/action cells that list, detail, flow, export, Job, and Task share
 * through {@link SurfaceGuard}. Export uses the IsolatedExport Task type so
 * submit / file / cache-status / dispatch decide the same grant.
 */
public final class SurfaceCatalog {
    public static final String EXPORT_TASK_TYPE = IsolatedExport.TASK_TYPE;
    public static final String EXPORT_RESOURCE = IsolatedExport.TASK_TYPE;
    public static final String EXPORT_ACTION = "submit";

    public static final String JOB_RESOURCE = "job";
    public static final String JOB_ACTION = "submit";

    public static final String TASK_RESOURCE = "task";
    public static final String TASK_ACTION = "submit";

    private SurfaceCatalog() {}

    public static boolean isExportTask(String taskType) {
        return EXPORT_TASK_TYPE.equals(taskType);
    }

    public static Decision requireExport(PermissionService service) {
        return requireExport(service, null);
    }

    public static Decision requireExport(PermissionService service, AuditPort audits) {
        return SurfaceGuard.require(service, audits, EXPORT_RESOURCE, EXPORT_ACTION);
    }

    public static Decision requireTask(PermissionService service, String taskType) {
        return requireTask(service, null, taskType);
    }

    public static Decision requireTask(PermissionService service, AuditPort audits, String taskType) {
        if (isExportTask(taskType)) {
            return requireExport(service, audits);
        }
        return SurfaceGuard.require(service, audits, TASK_RESOURCE, TASK_ACTION);
    }

    public static Decision requireJob(PermissionService service, String payloadType) {
        return requireJob(service, null, payloadType);
    }

    public static Decision requireJob(PermissionService service, AuditPort audits, String payloadType) {
        if (isExportTask(payloadType)) {
            return requireExport(service, audits);
        }
        return SurfaceGuard.require(service, audits, JOB_RESOURCE, JOB_ACTION);
    }
}
