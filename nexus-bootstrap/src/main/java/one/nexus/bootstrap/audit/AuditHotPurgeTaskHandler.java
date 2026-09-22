package one.nexus.bootstrap.audit;

import java.util.Objects;
import one.nexus.audit.AuditHotPurgeJob;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.TaskHandler;

/**
 * Job path for the 90-day hot-prefix purge. Requires a bound {@code PlatformContext}
 * ({@link AuditHotPurgeJob#PLATFORM_PATH}); tenant TaskQueue is not used.
 */
public final class AuditHotPurgeTaskHandler implements TaskHandler {
    private final AuditHotPurgeJob job;

    public AuditHotPurgeTaskHandler(AuditHotPurgeJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    @Override
    public boolean supports(String taskType) {
        return AuditHotPurgeJob.TASK_TYPE.equals(taskType);
    }

    @Override
    public void execute(IsolatedEnvelope envelope) {
        AccessContext context = AccessContextHolder.current();
        if (!context.platform()) {
            throw new NexusException(
                    ErrorCode.CTX_INVALID_PLATFORM,
                    403,
                    "audit hot purge Job requires PlatformContext " + AuditHotPurgeJob.PLATFORM_PATH);
        }
        job.run();
    }
}
