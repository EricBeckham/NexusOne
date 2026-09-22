package one.nexus.task.quartz;

import java.util.Properties;
import javax.sql.DataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

/**
 * Quartz 2.5.2 JDBC JobStoreTX / clustered factory used by product code and tests.
 * Table prefix {@code quartz.QRTZ_} matches Flyway V500 (P0-07 PostgreSQLDelegate).
 */
public final class QuartzSchedulers {
    public static final String TABLE_PREFIX = "quartz.QRTZ_";
    public static final String INSTANCE_NAME = "NexusOne";

    private QuartzSchedulers() {}

    public static Properties clusteredProperties() {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", INSTANCE_NAME);
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "4");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.tablePrefix", TABLE_PREFIX);
        properties.setProperty("org.quartz.jobStore.isClustered", "true");
        properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "2000");
        properties.setProperty("org.quartz.jobStore.misfireThreshold", "1000");
        return properties;
    }

    public static Scheduler jdbc(String jdbcUrl, String user, String password) throws SchedulerException {
        Properties properties = clusteredProperties();
        properties.setProperty("org.quartz.jobStore.dataSource", "nexus");
        properties.setProperty("org.quartz.dataSource.nexus.driver", "org.postgresql.Driver");
        properties.setProperty("org.quartz.dataSource.nexus.URL", jdbcUrl);
        properties.setProperty("org.quartz.dataSource.nexus.user", user);
        properties.setProperty("org.quartz.dataSource.nexus.password", password == null ? "" : password);
        properties.setProperty("org.quartz.dataSource.nexus.maxConnections", "8");
        return new StdSchedulerFactory(properties).getScheduler();
    }

    public static SchedulerFactoryBean springFactory(DataSource dataSource, boolean autoStartup) {
        Properties properties = clusteredProperties();
        properties.setProperty("org.quartz.jobStore.class", LocalDataSourceJobStore.class.getName());
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setOverwriteExistingJobs(false);
        factory.setAutoStartup(autoStartup);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setQuartzProperties(properties);
        factory.setSchedulerName(INSTANCE_NAME);
        return factory;
    }
}
