package poc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public final class Main {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55437/nexus_p0_07";

    private Main() { }

    private static String password() {
        String value = System.getenv("P0_07_DB_PASSWORD");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("P0_07_DB_PASSWORD must be set in this process");
        }
        return value;
    }

    private static Flyway flyway(String... locations) {
        return Flyway.configure()
                .dataSource(URL, "poc", password())
                .schemas("platform_static")
                .defaultSchema("platform_static")
                .locations(locations)
                .load();
    }

    private static void assertSql(String sql, long expected) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, "poc", password());
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            if (!r.next() || r.getLong(1) != expected) {
                throw new IllegalStateException("Assertion failed: " + sql + ", expected " + expected);
            }
        }
    }

    private static Scheduler scheduler() throws Exception {
        Properties p = new Properties();
        p.setProperty("org.quartz.scheduler.instanceName", "P007Cluster");
        p.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        p.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        p.setProperty("org.quartz.threadPool.threadCount", "4");
        p.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        p.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        p.setProperty("org.quartz.jobStore.dataSource", "poc");
        p.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        p.setProperty("org.quartz.jobStore.isClustered", "true");
        p.setProperty("org.quartz.jobStore.clusterCheckinInterval", "2000");
        p.setProperty("org.quartz.jobStore.misfireThreshold", "1000");
        p.setProperty("org.quartz.dataSource.poc.driver", "org.postgresql.Driver");
        p.setProperty("org.quartz.dataSource.poc.URL", URL + "?currentSchema=quartz");
        p.setProperty("org.quartz.dataSource.poc.user", "poc");
        p.setProperty("org.quartz.dataSource.poc.password", password());
        p.setProperty("org.quartz.dataSource.poc.maxConnections", "8");
        return new StdSchedulerFactory(p).getScheduler();
    }

    public static final class LogJob implements Job {
        @Override public void execute(JobExecutionContext context) throws JobExecutionException {
            String key = context.getJobDetail().getKey().getName();
            try (Connection c = DriverManager.getConnection(URL, "poc", password());
                 var ps = c.prepareStatement("INSERT INTO quartz.poc_fire_log (job_key, scheduler_instance) VALUES (?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, context.getScheduler().getSchedulerInstanceId());
                ps.executeUpdate();
                System.out.println("FIRED " + key + " on " + context.getScheduler().getSchedulerInstanceId());
            } catch (Exception e) {
                throw new JobExecutionException(e, false);
            }
        }
    }

    private static void schedule() throws Exception {
        Scheduler s = scheduler();
        try {
            Date start = Date.from(Instant.now().plusSeconds(12));
            for (int i = 0; i < 20; i++) {
                String key = "cluster-" + i;
                s.scheduleJob(JobBuilder.newJob(LogJob.class).withIdentity(key, "poc").build(),
                        TriggerBuilder.newTrigger().withIdentity(key, "poc").startAt(start).build());
            }
            s.scheduleJob(JobBuilder.newJob(LogJob.class).withIdentity("misfire", "poc").build(),
                    TriggerBuilder.newTrigger().withIdentity("misfire", "poc")
                            .startAt(Date.from(Instant.now().minusSeconds(30)))
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                            .build());
            System.out.println("SCHEDULED 20 cluster jobs and one past-due misfire; start two nodes now");
        } finally {
            s.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass one mode; see README");
        switch (args[0]) {
            case "flyway-base" -> { flyway("filesystem:migrations/base").migrate(); System.out.println("PASS: V1/V2 migrated"); }
            case "flyway-fail" -> {
                try {
                    flyway("filesystem:migrations/base", "filesystem:migrations/failing").migrate();
                    throw new IllegalStateException("Expected V3 failure did not occur");
                } catch (org.flywaydb.core.api.FlywayException expected) {
                    assertSql("SELECT count(*) FROM information_schema.tables WHERE table_schema='platform_static' AND table_name='poc_must_rollback'", 0);
                    System.out.println("PASS: failing V3 rolled back without partial table");
                }
            }
            case "flyway-recover" -> {
                flyway("filesystem:migrations/base", "filesystem:migrations/recovered").migrate();
                flyway("filesystem:migrations/base", "filesystem:migrations/recovered").validate();
                assertSql("SELECT count(*) FROM platform_static.poc_recovered", 1);
                assertSql("SELECT count(*) FROM platform_static.flyway_schema_history WHERE success", 3);
                System.out.println("PASS: recovered V3 and schema history validated");
            }
            case "quartz-schedule" -> schedule();
            case "quartz-node" -> {
                Scheduler s = scheduler();
                s.start();
                try { Thread.sleep(30_000); } finally { s.shutdown(true); }
            }
            case "quartz-check" -> {
                assertSql("SELECT count(*) FROM quartz.poc_fire_log WHERE job_key LIKE 'cluster-%'", 20);
                assertSql("SELECT count(*) FROM quartz.poc_fire_log WHERE job_key='misfire'", 1);
                System.out.println("PASS: 20 unique clustered executions and one misfire execution");
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
    }
}
