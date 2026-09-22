package one.nexus.bootstrap.audit;

import one.nexus.audit.AuditColdArchiveJob;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.TaskHandler;
import one.nexus.task.quartz.ExecutionRuntime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in platform Quartz entry for cold archive. Does not use IsolatedQuartzJob
 * (that restore path requires tenant membership).
 */
public final class PlatformAuditColdArchiveQuartzJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformAuditColdArchiveQuartzJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        ExecutionRuntime runtime = ExecutionRuntime.get();
        ExecutionIsolation isolation = runtime.isolation();
        String trace = "audit-cold-" + context.getFireTime().toInstant();
        PlatformContext platform = isolation.restorePlatform(
                AuditColdArchiveJob.PLATFORM_PATH,
                Actor.system("audit-cold-archive"),
                "object-store cold archive copy",
                trace);
        try {
            AccessContextHolder.run(platform, () -> {
                IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(AuditColdArchiveJob.TASK_TYPE, "{}");
                TaskHandler handler = runtime.jobHandlers().stream()
                        .filter(candidate -> candidate.supports(envelope.payloadType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no handler for " + envelope.payloadType()));
                handler.execute(envelope);
            });
        } catch (RuntimeException ex) {
            LOG.warn("audit cold archive Job failed trace={}", trace, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}
