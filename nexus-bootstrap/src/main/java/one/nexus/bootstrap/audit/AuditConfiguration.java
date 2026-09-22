package one.nexus.bootstrap.audit;

import java.time.Clock;
import java.util.TimeZone;
import javax.sql.DataSource;
import one.nexus.audit.AuditArchiveJob;
import one.nexus.audit.AuditArchivePort;
import one.nexus.audit.AuditColdArchive;
import one.nexus.audit.AuditColdArchiveJob;
import one.nexus.audit.AuditColdArchivePort;
import one.nexus.audit.AuditHotPurge;
import one.nexus.audit.AuditHotPurgeJob;
import one.nexus.audit.AuditHotPurgePort;
import one.nexus.audit.AuditObjectStore;
import one.nexus.audit.AuditPort;
import one.nexus.audit.InMemoryAuditArchivePort;
import one.nexus.audit.InMemoryAuditObjectStore;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.audit.JdbcAuditArchivePort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.audit.ObjectStorageAuditStore;
import one.nexus.audit.recovery.DryRunRecoveryPort;
import one.nexus.audit.recovery.RecoveryPort;
import one.nexus.crypto.CryptoPort;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.file.FileProperties;
import one.nexus.file.ObjectStoragePort;
import one.nexus.p1.infra.audit.JdbcAuditPort;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AuditConfiguration {
    @Bean
    AuditPort auditPort(
            CryptoPort cryptoPort,
            ObjectProvider<DataSource> dataSources,
            @Value("${nexus.audit.persistence:jdbc}") String persistence) {
        if ("jdbc".equalsIgnoreCase(persistence)) {
            DataSource dataSource = dataSources.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("nexus.audit.persistence=jdbc requires a DataSource");
            }
            return new JdbcHashChainedAuditPort(dataSource, cryptoPort);
        }
        return new InMemoryHashChainedAuditPort(cryptoPort);
    }

    @Bean
    AuditArchivePort auditArchivePort(
            AuditPort auditPort,
            ObjectProvider<DataSource> dataSources,
            @Value("${nexus.audit.persistence:jdbc}") String persistence) {
        if ("jdbc".equalsIgnoreCase(persistence)) {
            DataSource dataSource = dataSources.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("nexus.audit.persistence=jdbc requires a DataSource");
            }
            return new JdbcAuditArchivePort(dataSource);
        }
        return new InMemoryAuditArchivePort(auditPort);
    }

    @Bean
    AuditArchiveJob auditArchiveJob(AuditArchivePort archive, ObjectProvider<Clock> clocks) {
        return new AuditArchiveJob(archive, clocks.orderedStream().findFirst().orElseGet(Clock::systemUTC));
    }

    @Bean
    AuditArchiveTaskHandler auditArchiveTaskHandler(AuditArchiveJob job) {
        return new AuditArchiveTaskHandler(job);
    }

    @Bean
    @ConditionalOnBean(ExecutionIsolation.class)
    AuditArchiveRunner auditArchiveRunner(
            ExecutionIsolation isolation, AuditArchiveTaskHandler handler, AuditArchiveJob job) {
        return new AuditArchiveRunner(isolation, handler, job);
    }

    @Bean
    AuditObjectStore auditObjectStore(
            ObjectProvider<ObjectStoragePort> storages, ObjectProvider<FileProperties> files) {
        return objectStore(storages.getIfAvailable(), files.getIfAvailable());
    }

    @Bean
    AuditColdArchivePort auditColdArchivePort(
            AuditPort auditPort, AuditArchivePort archive, AuditObjectStore store) {
        if (archive instanceof JdbcAuditArchivePort jdbc) {
            return AuditColdArchive.jdbc(jdbc, store);
        }
        return new AuditColdArchive(auditPort, archive, store);
    }

    @Bean
    AuditColdArchiveJob auditColdArchiveJob(AuditColdArchivePort cold, ObjectProvider<Clock> clocks) {
        return new AuditColdArchiveJob(cold, clocks.orderedStream().findFirst().orElseGet(Clock::systemUTC));
    }

    @Bean
    AuditColdArchiveTaskHandler auditColdArchiveTaskHandler(AuditColdArchiveJob job) {
        return new AuditColdArchiveTaskHandler(job);
    }

    @Bean
    @ConditionalOnBean(ExecutionIsolation.class)
    AuditColdArchiveRunner auditColdArchiveRunner(
            ExecutionIsolation isolation, AuditColdArchiveTaskHandler handler, AuditColdArchiveJob job) {
        return new AuditColdArchiveRunner(isolation, handler, job);
    }

    @Bean
    AuditHotPurgePort auditHotPurgePort(
            AuditPort auditPort,
            AuditArchivePort archive,
            AuditObjectStore store,
            ObjectProvider<DataSource> dataSources,
            @Value("${nexus.audit.persistence:jdbc}") String persistence) {
        if ("jdbc".equalsIgnoreCase(persistence)) {
            DataSource dataSource = dataSources.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("nexus.audit.persistence=jdbc requires a DataSource");
            }
            return AuditHotPurge.jdbc(auditPort, archive, store, dataSource);
        }
        if (auditPort instanceof InMemoryHashChainedAuditPort memory) {
            return AuditHotPurge.inMemory(memory, archive, store);
        }
        throw new IllegalStateException("hot purge requires JDBC or InMemoryHashChainedAuditPort");
    }

    @Bean
    AuditHotPurgeJob auditHotPurgeJob(AuditHotPurgePort purge, ObjectProvider<Clock> clocks) {
        return new AuditHotPurgeJob(purge, clocks.orderedStream().findFirst().orElseGet(Clock::systemUTC));
    }

    @Bean
    AuditHotPurgeTaskHandler auditHotPurgeTaskHandler(AuditHotPurgeJob job) {
        return new AuditHotPurgeTaskHandler(job);
    }

    @Bean
    @ConditionalOnBean(ExecutionIsolation.class)
    AuditHotPurgeRunner auditHotPurgeRunner(
            ExecutionIsolation isolation, AuditHotPurgeTaskHandler handler, AuditHotPurgeJob job) {
        return new AuditHotPurgeRunner(isolation, handler, job);
    }

    @Bean
    RecoveryPort recoveryPort() {
        return new DryRunRecoveryPort();
    }

    /**
     * P1 tenant/quota/admin writes keep staging {@code platform.audit_event} and
     * also land on the existing hash chain. Same method as the caller used:
     * {@code append} joins the business TX; {@code appendNewTransaction} commits
     * privately (disable-reject / quota-reject).
     */
    @Bean
    @Primary
    @ConditionalOnBean(JdbcAuditPort.class)
    one.nexus.p1.port.AuditPort hashChainedP1AuditPort(JdbcAuditPort staging, AuditPort chain) {
        return new HashChainedP1AuditPort(staging, chain);
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnProperty(name = "nexus.audit.archive.schedule-enabled", havingValue = "true")
    org.springframework.beans.factory.InitializingBean auditArchiveSchedule(Scheduler scheduler) {
        return () -> scheduler.scheduleJob(
                JobBuilder.newJob(PlatformAuditArchiveQuartzJob.class)
                        .withIdentity("audit-archive-copy", "nexus-platform")
                        .requestRecovery(true)
                        .storeDurably()
                        .build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("audit-archive-copy", "nexus-platform")
                        .withSchedule(CronScheduleBuilder.cronSchedule("0 30 2 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                                .withMisfireHandlingInstructionDoNothing())
                        .build());
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnProperty(name = "nexus.audit.cold-archive.schedule-enabled", havingValue = "true")
    org.springframework.beans.factory.InitializingBean auditColdArchiveSchedule(Scheduler scheduler) {
        return () -> scheduler.scheduleJob(
                JobBuilder.newJob(PlatformAuditColdArchiveQuartzJob.class)
                        .withIdentity("audit-archive-cold", "nexus-platform")
                        .requestRecovery(true)
                        .storeDurably()
                        .build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("audit-archive-cold", "nexus-platform")
                        .withSchedule(CronScheduleBuilder.cronSchedule("0 45 2 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                                .withMisfireHandlingInstructionDoNothing())
                        .build());
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnProperty(name = "nexus.audit.hot-purge.schedule-enabled", havingValue = "true")
    org.springframework.beans.factory.InitializingBean auditHotPurgeSchedule(Scheduler scheduler) {
        return () -> scheduler.scheduleJob(
                JobBuilder.newJob(PlatformAuditHotPurgeQuartzJob.class)
                        .withIdentity("audit-hot-purge", "nexus-platform")
                        .requestRecovery(true)
                        .storeDurably()
                        .build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("audit-hot-purge", "nexus-platform")
                        .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                                .withMisfireHandlingInstructionDoNothing())
                        .build());
    }

    private static AuditObjectStore objectStore(ObjectStoragePort storage, FileProperties files) {
        if (storage == null) {
            return new InMemoryAuditObjectStore();
        }
        String bucket = files != null && "s3".equalsIgnoreCase(files.getStorage())
                ? files.getS3().getBucket()
                : files == null || files.getBucketAlias() == null || files.getBucketAlias().isBlank()
                        ? "nexus-files"
                        : files.getBucketAlias();
        return new ObjectStorageAuditStore(
                bucket, storage::put, storage::get, storage::exists, storage::listKeys);
    }
}
