package one.nexus.bootstrap.audit;

import one.nexus.audit.AuditArchiveJob;
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
 * Opt-in platform Quartz entry. Does not use {@code IsolatedQuartzJob} (that
 * restore path requires tenant membership).
 */
public final class PlatformAuditArchiveQuartzJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformAuditArchiveQuartzJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        ExecutionRuntime runtime = ExecutionRuntime.get();
        ExecutionIsolation isolation = runtime.isolation();
        String trace = "audit-archive-" + context.getFireTime().toInstant();
        PlatformContext platform = isolation.restorePlatform(
                AuditArchiveJob.PLATFORM_PATH,
                Actor.system("audit-archive"),
                "90-day hot copy",
                trace);
        try {
            AccessContextHolder.run(platform, () -> {
                IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(AuditArchiveJob.TASK_TYPE, "{}");
                TaskHandler handler = runtime.jobHandlers().stream()
                        .filter(candidate -> candidate.supports(envelope.payloadType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no handler for " + envelope.payloadType()));
                handler.execute(envelope);
            });
        } catch (RuntimeException ex) {
            LOG.warn("audit archive Job failed trace={}", trace, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}
