package one.nexus.bootstrap.audit;

import java.util.Objects;
import java.util.UUID;
import one.nexus.audit.AuditHotPurgeJob;
import one.nexus.audit.HotPurgeRunResult;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Issues {@code PlatformContext} {@link AuditHotPurgeJob#PLATFORM_PATH} and
 * runs the hot-purge TaskHandler. IsolatedJobScheduler stays tenant-only.
 */
public final class AuditHotPurgeRunner {
    private final ExecutionIsolation isolation;
    private final AuditHotPurgeTaskHandler handler;
    private final AuditHotPurgeJob job;

    public AuditHotPurgeRunner(
            ExecutionIsolation isolation, AuditHotPurgeTaskHandler handler, AuditHotPurgeJob job) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.job = Objects.requireNonNull(job, "job");
    }

    public HotPurgeRunResult runOnce(String traceId) {
        String trace = (traceId == null || traceId.isBlank())
                ? "audit-hot-purge-" + UUID.randomUUID()
                : traceId;
        PlatformContext platform = isolation.restorePlatform(
                AuditHotPurgeJob.PLATFORM_PATH,
                Actor.system("audit-hot-purge"),
                "90-day hot-prefix purge of copied rows",
                trace);
        return AccessContextHolder.call(platform, () -> {
            IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(AuditHotPurgeJob.TASK_TYPE, "{}");
            handler.execute(envelope);
            return job.lastResult();
        });
    }
}
