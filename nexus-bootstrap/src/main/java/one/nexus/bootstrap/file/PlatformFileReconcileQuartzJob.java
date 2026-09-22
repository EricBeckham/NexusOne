package one.nexus.bootstrap.file;

import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.file.FileReconcileJob;
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
 * Opt-in platform Quartz entry. Does not use IsolatedQuartzJob (that restore
 * path requires tenant membership). Preview only; apply stays on the API.
 */
public final class PlatformFileReconcileQuartzJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformFileReconcileQuartzJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        ExecutionRuntime runtime = ExecutionRuntime.get();
        ExecutionIsolation isolation = runtime.isolation();
        String trace = "files-reconcile-" + context.getFireTime().toInstant();
        PlatformContext platform = isolation.restorePlatform(
                FileReconcileJob.PLATFORM_PATH,
                Actor.system("files-reconcile"),
                "file metadata object reconcile",
                trace);
        try {
            AccessContextHolder.run(platform, () -> {
                IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(
                        FileReconcileJob.TASK_TYPE, "{\"apply\":false}");
                TaskHandler handler = runtime.jobHandlers().stream()
                        .filter(candidate -> candidate.supports(envelope.payloadType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no handler for " + envelope.payloadType()));
                handler.execute(envelope);
            });
        } catch (RuntimeException ex) {
            LOG.warn("file reconcile Job failed trace={}", trace, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}
