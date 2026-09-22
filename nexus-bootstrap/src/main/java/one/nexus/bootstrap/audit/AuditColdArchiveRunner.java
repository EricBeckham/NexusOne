package one.nexus.bootstrap.audit;

import java.util.Objects;
import java.util.UUID;
import one.nexus.audit.AuditColdArchiveJob;
import one.nexus.audit.ColdArchiveRunResult;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Issues {@code PlatformContext} {@link AuditColdArchiveJob#PLATFORM_PATH} and
 * runs the cold-archive TaskHandler. IsolatedJobScheduler stays tenant-only.
 */
public final class AuditColdArchiveRunner {
    private final ExecutionIsolation isolation;
    private final AuditColdArchiveTaskHandler handler;
    private final AuditColdArchiveJob job;

    public AuditColdArchiveRunner(
            ExecutionIsolation isolation, AuditColdArchiveTaskHandler handler, AuditColdArchiveJob job) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.job = Objects.requireNonNull(job, "job");
    }

    public ColdArchiveRunResult runOnce(String traceId) {
        String trace = (traceId == null || traceId.isBlank())
                ? "audit-cold-" + UUID.randomUUID()
                : traceId;
        PlatformContext platform = isolation.restorePlatform(
                AuditColdArchiveJob.PLATFORM_PATH,
                Actor.system("audit-cold-archive"),
                "object-store cold archive copy",
                trace);
        return AccessContextHolder.call(platform, () -> {
            IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(AuditColdArchiveJob.TASK_TYPE, "{}");
            handler.execute(envelope);
            return job.lastResult();
        });
    }
}
