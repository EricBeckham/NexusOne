package one.nexus.task.quartz;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;

public final class IsolatedJobScheduler {
    public static final String DEFAULT_GROUP = "nexus";

    private final Scheduler scheduler;
    private final ExecutionIsolation isolation;

    public IsolatedJobScheduler(Scheduler scheduler, ExecutionIsolation isolation) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
    }

    public void scheduleOnce(String name, Instant startAt, String payloadType, String payload) {
        IsolatedEnvelope envelope = isolation.envelopeFromCurrent(payloadType, payload);
        StoredMembership membership = isolation.requireTenantMembership();
        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(IsolatedQuartzJob.class)
                            .withIdentity(name, DEFAULT_GROUP)
                            .usingJobData(IsolatedQuartzJob.dataMap(membership, envelope))
                            .requestRecovery(true)
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity(name, DEFAULT_GROUP)
                            .startAt(Date.from(startAt == null ? Instant.now() : startAt))
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                    .withMisfireHandlingInstructionFireNow())
                            .build());
        } catch (SchedulerException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void scheduleCron(String name, String cron, String payloadType, String payload) {
        IsolatedEnvelope envelope = isolation.envelopeFromCurrent(payloadType, payload);
        StoredMembership membership = isolation.requireTenantMembership();
        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(IsolatedQuartzJob.class)
                            .withIdentity(name, DEFAULT_GROUP)
                            .usingJobData(IsolatedQuartzJob.dataMap(membership, envelope))
                            .requestRecovery(true)
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity(name, DEFAULT_GROUP)
                            .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                                    .inTimeZone(TimeZone.getTimeZone("UTC"))
                                    .withMisfireHandlingInstructionFireAndProceed())
                            .build());
        } catch (SchedulerException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public boolean delete(String name) {
        try {
            return scheduler.deleteJob(JobKey.jobKey(name, DEFAULT_GROUP));
        } catch (SchedulerException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Scheduler scheduler() {
        return scheduler;
    }
}
