package one.nexus.bootstrap.audit;

import java.util.Objects;
import java.util.UUID;
import one.nexus.audit.ArchiveRunResult;
import one.nexus.audit.AuditArchiveJob;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Issues {@code PlatformContext} {@link AuditArchiveJob#PLATFORM_PATH} and runs
 * the archive TaskHandler. IsolatedJobScheduler / TaskQueue stay tenant-only.
 */
public final class AuditArchiveRunner {
    private final ExecutionIsolation isolation;
    private final AuditArchiveTaskHandler handler;
    private final AuditArchiveJob job;

    public AuditArchiveRunner(
            ExecutionIsolation isolation, AuditArchiveTaskHandler handler, AuditArchiveJob job) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.job = Objects.requireNonNull(job, "job");
    }

    public ArchiveRunResult runOnce(String traceId) {
        String trace = (traceId == null || traceId.isBlank())
                ? "audit-archive-" + UUID.randomUUID()
                : traceId;
        PlatformContext platform = isolation.restorePlatform(
                AuditArchiveJob.PLATFORM_PATH,
                Actor.system("audit-archive"),
                "90-day hot copy",
                trace);
        return AccessContextHolder.call(platform, () -> {
            IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(AuditArchiveJob.TASK_TYPE, "{}");
            handler.execute(envelope);
            return job.lastResult();
        });
    }
}
