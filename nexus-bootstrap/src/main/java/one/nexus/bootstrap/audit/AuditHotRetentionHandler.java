package one.nexus.bootstrap.audit;

import java.util.Objects;
import one.nexus.audit.AuditHotRetentionJob;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.task.TaskHandler;

/** Task / Quartz handler for {@link AuditHotRetentionJob#TASK_TYPE}. Dry-run only. */
public final class AuditHotRetentionHandler implements TaskHandler {
    private final AuditHotRetentionJob job;

    public AuditHotRetentionHandler(AuditHotRetentionJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    @Override
    public boolean supports(String taskType) {
        return AuditHotRetentionJob.TASK_TYPE.equals(taskType);
    }

    @Override
    public void execute(IsolatedEnvelope envelope) {
        job.scan();
    }
}
