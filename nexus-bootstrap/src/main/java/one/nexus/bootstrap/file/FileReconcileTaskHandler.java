package one.nexus.bootstrap.file;

import java.util.Objects;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.file.FileObjectReconciler;
import one.nexus.file.FileReconcileJob;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.TaskHandler;

/**
 * Job path for G10 file metadata ↔ object store reconcile. Tenant IsolatedQuartzJob
 * / TaskQueue restore TenantContext; the platform runner issues {@code files.reconcile}.
 */
public final class FileReconcileTaskHandler implements TaskHandler {
    private final FileReconcileJob job;

    public FileReconcileTaskHandler(FileReconcileJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    @Override
    public boolean supports(String taskType) {
        return FileReconcileJob.TASK_TYPE.equals(taskType);
    }

    @Override
    public void execute(IsolatedEnvelope envelope) {
        boolean apply = FileObjectReconciler.applyFlag(envelope.payload());
        AccessContext context = AccessContextHolder.current();
        if (context instanceof TenantContext tenant) {
            job.run(apply, UUID.fromString(tenant.tenantId()));
            return;
        }
        if (context instanceof PlatformContext platform
                && FileReconcileJob.PLATFORM_PATH.equals(platform.path())) {
            job.run(apply);
            return;
        }
        throw new NexusException(
                ErrorCode.CTX_INVALID_PLATFORM,
                403,
                "file reconcile Job requires TenantContext or PlatformContext "
                        + FileReconcileJob.PLATFORM_PATH);
    }
}
