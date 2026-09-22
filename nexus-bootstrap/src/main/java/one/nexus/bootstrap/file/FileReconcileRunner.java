package one.nexus.bootstrap.file;

import java.util.Objects;
import java.util.UUID;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.file.FileObjectReconciler;
import one.nexus.file.FileReconcileJob;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Issues {@code PlatformContext} {@link FileReconcileJob#PLATFORM_PATH} and runs
 * the reconcile TaskHandler. IsolatedJobScheduler stays tenant-only.
 */
public final class FileReconcileRunner {
    private final ExecutionIsolation isolation;
    private final FileReconcileTaskHandler handler;
    private final FileReconcileJob job;

    public FileReconcileRunner(
            ExecutionIsolation isolation, FileReconcileTaskHandler handler, FileReconcileJob job) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.job = Objects.requireNonNull(job, "job");
    }

    public FileObjectReconciler.Report runOnce(boolean apply, String traceId) {
        String trace = (traceId == null || traceId.isBlank())
                ? "files-reconcile-" + UUID.randomUUID()
                : traceId;
        PlatformContext platform = isolation.restorePlatform(
                FileReconcileJob.PLATFORM_PATH,
                Actor.system("files-reconcile"),
                "file metadata object reconcile",
                trace);
        return AccessContextHolder.call(platform, () -> {
            String payload = apply ? "{\"apply\":true}" : "{\"apply\":false}";
            IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(FileReconcileJob.TASK_TYPE, payload);
            handler.execute(envelope);
            return job.lastResult();
        });
    }
}
