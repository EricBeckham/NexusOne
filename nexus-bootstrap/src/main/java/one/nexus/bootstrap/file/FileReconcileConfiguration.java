package one.nexus.bootstrap.file;

import java.util.TimeZone;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.file.FileReconcileJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileReconcileConfiguration {
    @Bean
    @ConditionalOnBean(FileReconcileJob.class)
    FileReconcileTaskHandler fileReconcileTaskHandler(FileReconcileJob job) {
        return new FileReconcileTaskHandler(job);
    }

    @Bean
    @ConditionalOnBean({ExecutionIsolation.class, FileReconcileJob.class})
    FileReconcileRunner fileReconcileRunner(
            ExecutionIsolation isolation, FileReconcileTaskHandler handler, FileReconcileJob job) {
        return new FileReconcileRunner(isolation, handler, job);
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnProperty(name = "nexus.files.reconcile.schedule-enabled", havingValue = "true")
    org.springframework.beans.factory.InitializingBean fileReconcileSchedule(Scheduler scheduler) {
        return () -> scheduler.scheduleJob(
                JobBuilder.newJob(PlatformFileReconcileQuartzJob.class)
                        .withIdentity("files-reconcile", "nexus-platform")
                        .requestRecovery(true)
                        .storeDurably()
                        .build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("files-reconcile", "nexus-platform")
                        .withSchedule(CronScheduleBuilder.cronSchedule("0 15 3 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                                .withMisfireHandlingInstructionDoNothing())
                        .build());
    }
}
