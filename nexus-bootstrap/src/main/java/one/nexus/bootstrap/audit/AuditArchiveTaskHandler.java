package one.nexus.bootstrap.audit;

import java.util.Objects;
import one.nexus.audit.AuditArchiveJob;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.TaskHandler;

/**
 * Job path for the 90-day hot copy and 365-day archive purge. Requires a bound {@code PlatformContext}
 * ({@link AuditArchiveJob#PLATFORM_PATH}); tenant TaskQueue submit is not used
 * because IsolatedJobScheduler requires membership.
 */
public final class AuditArchiveTaskHandler implements TaskHandler {
    private final AuditArchiveJob job;

    public AuditArchiveTaskHandler(AuditArchiveJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    @Override
    public boolean supports(String taskType) {
        return AuditArchiveJob.TASK_TYPE.equals(taskType);
    }

    @Override
    public void execute(IsolatedEnvelope envelope) {
        AccessContext context = AccessContextHolder.current();
        if (!context.platform()) {
            throw new NexusException(
                    ErrorCode.CTX_INVALID_PLATFORM,
                    403,
                    "audit archive Job requires PlatformContext " + AuditArchiveJob.PLATFORM_PATH);
        }
        job.run();
    }
}
