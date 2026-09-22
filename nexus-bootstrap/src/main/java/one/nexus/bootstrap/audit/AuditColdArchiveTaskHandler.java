package one.nexus.bootstrap.audit;

import java.util.Objects;
import one.nexus.audit.AuditColdArchiveJob;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.TaskHandler;

/**
 * Job path for object-store cold archive. Requires a bound {@code PlatformContext}
 * ({@link AuditColdArchiveJob#PLATFORM_PATH}); tenant TaskQueue is not used.
 */
public final class AuditColdArchiveTaskHandler implements TaskHandler {
    private final AuditColdArchiveJob job;

    public AuditColdArchiveTaskHandler(AuditColdArchiveJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    @Override
    public boolean supports(String taskType) {
        return AuditColdArchiveJob.TASK_TYPE.equals(taskType);
    }

    @Override
    public void execute(IsolatedEnvelope envelope) {
        AccessContext context = AccessContextHolder.current();
        if (!context.platform()) {
            throw new NexusException(
                    ErrorCode.CTX_INVALID_PLATFORM,
                    403,
                    "audit cold archive Job requires PlatformContext " + AuditColdArchiveJob.PLATFORM_PATH);
        }
        job.run();
    }
}
