package one.nexus.task;

import java.time.Clock;
import javax.sql.DataSource;
import one.nexus.event.DeadLetterStore;
import one.nexus.event.ExecutionProperties;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.JobLogWriter;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.task.jdbc.JdbcTaskQueue;
import one.nexus.task.quartz.ExecutionRuntime;
import one.nexus.task.quartz.IsolatedJobScheduler;
import one.nexus.task.quartz.QuartzSchedulers;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Configuration
@ConditionalOnBean(DataSource.class)
public class TaskCoreConfiguration {
    @Bean
    @ConditionalOnMissingBean
    JdbcTaskQueue jdbcTaskQueue(
            DataSource dataSource,
            ExecutionIsolation isolation,
            ExecutionSettings settings,
            Clock executionClock
    ) {
        return new JdbcTaskQueue(dataSource, isolation, settings, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean(TaskQueue.class)
    TaskQueue taskQueue(JdbcTaskQueue queue) {
        return queue;
    }

    @Bean
    @ConditionalOnMissingBean
    TaskWorker taskWorker(
            JdbcTaskQueue queue,
            ExecutionIsolation isolation,
            ObjectProvider<TaskHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog,
            ExecutionSettings settings,
            Clock executionClock,
            ExecutionProperties properties
    ) {
        return new TaskWorker(
                queue,
                isolation,
                handlers.orderedStream().toList(),
                idempotency,
                deadLetters,
                jobLog,
                settings,
                executionClock,
                properties.workerEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    ExecutionRuntime executionRuntime(
            ExecutionIsolation isolation,
            ObjectProvider<TaskHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog
    ) {
        ExecutionRuntime runtime = new ExecutionRuntime(
                isolation,
                handlers.orderedStream().toList(),
                idempotency,
                deadLetters,
                jobLog);
        ExecutionRuntime.install(runtime);
        return runtime;
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.execution.quartz-enabled", matchIfMissing = true)
    SchedulerFactoryBean nexusSchedulerFactoryBean(DataSource dataSource, ExecutionProperties properties) {
        return QuartzSchedulers.springFactory(dataSource, properties.quartzAutoStartup());
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnMissingBean
    IsolatedJobScheduler isolatedJobScheduler(Scheduler scheduler, ExecutionIsolation isolation) {
        return new IsolatedJobScheduler(scheduler, isolation);
    }
}
