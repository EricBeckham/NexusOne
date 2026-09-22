package one.nexus.task.quartz;

import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.task.TaskHandler;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz entry that restores tenant context through M0-02
 * {@link one.nexus.isolation.context.RequestIsolationPolicy} before the handler runs.
 */
public final class IsolatedQuartzJob implements Job {
    public static final String TENANT_ID = "tenantId";
    public static final String TRACE_ID = "traceId";
    public static final String ACTOR_ID = "actorId";
    public static final String ACTOR_TYPE = "actorType";
    public static final String MEMBERSHIP_ID = "membershipId";
    public static final String PAYLOAD_TYPE = "payloadType";
    public static final String PAYLOAD = "payload";

    private static final Logger LOG = LoggerFactory.getLogger(IsolatedQuartzJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        StoredMembership membership = new StoredMembership(
                required(data, TENANT_ID),
                required(data, ACTOR_ID),
                required(data, ACTOR_TYPE),
                required(data, MEMBERSHIP_ID));
        IsolatedEnvelope envelope = new IsolatedEnvelope(
                membership.tenantId(),
                required(data, TRACE_ID),
                required(data, PAYLOAD_TYPE),
                data.getString(PAYLOAD) == null ? "" : data.getString(PAYLOAD));
        ExecutionRuntime runtime = ExecutionRuntime.get();
        String jobKey = context.getJobDetail().getKey().toString();
        try {
            runtime.isolation().runRestored(membership, envelope, item -> handle(runtime, item));
            if (runtime.jobLog() != null) {
                runtime.jobLog().write("QUARTZ", null, jobKey, envelope.tenantId(), envelope.traceId(), "FIRED",
                        context.isRecovering() ? "recover" : "normal");
            }
        } catch (RuntimeException ex) {
            LOG.warn("quartz job failed key={} tenant={} trace={}", jobKey, envelope.tenantId(), envelope.traceId(), ex);
            if (runtime.deadLetters() != null) {
                runtime.deadLetters().record(
                        "QUARTZ",
                        new UUID(0, 0),
                        envelope.tenantId(),
                        envelope.traceId(),
                        envelope.payloadType(),
                        envelope.payload(),
                        ex.getMessage(),
                        1);
            }
            throw new JobExecutionException(ex, false);
        }
    }

    private static void handle(ExecutionRuntime runtime, IsolatedEnvelope envelope) {
        TaskHandler handler = runtime.jobHandlers().stream()
                .filter(candidate -> candidate.supports(envelope.payloadType()))
                .findFirst()
                .orElseThrow(() -> new NexusException(
                        ErrorCode.JOB_UNKNOWN_TYPE, 500, "no quartz handler for " + envelope.payloadType()));
        String key = envelope.traceId() + ":" + envelope.payloadType() + ":" + envelope.payload().hashCode();
        if (runtime.idempotency().exists("quartz-handler:" + envelope.payloadType(), key)) {
            return;
        }
        handler.execute(envelope);
        runtime.idempotency().claim(
                "quartz-handler:" + envelope.payloadType(),
                key,
                envelope.tenantId(),
                envelope.traceId());
    }

    public static JobDataMap dataMap(StoredMembership membership, IsolatedEnvelope envelope) {
        JobDataMap map = new JobDataMap();
        map.put(TENANT_ID, membership.tenantId());
        map.put(TRACE_ID, envelope.traceId());
        map.put(ACTOR_ID, membership.actorId());
        map.put(ACTOR_TYPE, membership.actorType());
        map.put(MEMBERSHIP_ID, membership.membershipId());
        map.put(PAYLOAD_TYPE, envelope.payloadType());
        map.put(PAYLOAD, envelope.payload());
        return map;
    }

    private static String required(JobDataMap data, String key) {
        String value = data.getString(key);
        if (value == null || value.isBlank()) {
            throw new NexusException(ErrorCode.JOB_UNKNOWN_TYPE, 500, "quartz job data missing " + key);
        }
        return value;
    }
}
