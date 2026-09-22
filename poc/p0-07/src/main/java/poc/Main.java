package poc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String URL = System.getenv().getOrDefault(
            "P0_07_JDBC_URL", "jdbc:postgresql://127.0.0.1:55437/nexus_p0_07");
    private static final String USER = System.getenv().getOrDefault("P0_07_DB_USER", "poc");

    private Main() { }

    private static String password() {
        return System.getenv().getOrDefault("P0_07_DB_PASSWORD", "");
    }

    private static Flyway flyway(String... locations) {
        return Flyway.configure()
                .dataSource(URL, USER, password())
                .schemas("platform_static")
                .defaultSchema("platform_static")
                .locations(locations)
                .load();
    }

    private static void assertSql(String sql, long expected) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, password());
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
        p.setProperty("org.quartz.dataSource.poc.user", USER);
        p.setProperty("org.quartz.dataSource.poc.password", password());
        p.setProperty("org.quartz.dataSource.poc.maxConnections", "8");
        return new StdSchedulerFactory(p).getScheduler();
    }

    public static final class LogJob implements Job {
        @Override public void execute(JobExecutionContext context) throws JobExecutionException {
            String key = context.getJobDetail().getKey().getName();
            try (Connection c = DriverManager.getConnection(URL, USER, password());
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

    private static String failoverId() {
        String id = System.getenv("P0_07_FAILOVER_ID");
        if (id == null || !id.matches("[a-zA-Z0-9-]{8,80}")) {
            throw new IllegalArgumentException("Set P0_07_FAILOVER_ID to an 8-80 character alphanumeric/hyphen run ID");
        }
        return id;
    }

    private static void ensureRecoveryLog() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, password());
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS quartz.poc_recovery_log (id bigserial PRIMARY KEY, run_id varchar(80) NOT NULL, phase varchar(30) NOT NULL, scheduler_instance varchar(200) NOT NULL, at_time timestamptz NOT NULL DEFAULT now())");
        }
    }

    private static void recoveryLog(String runId, String phase, String instance) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, password());
             PreparedStatement s = c.prepareStatement("INSERT INTO quartz.poc_recovery_log (run_id, phase, scheduler_instance) VALUES (?, ?, ?)")) {
            s.setString(1, runId);
            s.setString(2, phase);
            s.setString(3, instance);
            s.executeUpdate();
        }
    }

    public static final class RecoveryJob implements Job {
        @Override public void execute(JobExecutionContext context) throws JobExecutionException {
            String runId = context.getJobDetail().getJobDataMap().getString("runId");
            try {
                String instance = context.getScheduler().getSchedulerInstanceId();
                String phase = context.isRecovering() ? "RECOVER_START" : "START";
                recoveryLog(runId, phase, instance);
                System.out.println("FAILOVER " + phase + " " + runId + " on " + instance);
                if (!context.isRecovering()) Thread.sleep(60_000);
                recoveryLog(runId, "COMPLETE", instance);
                System.out.println("FAILOVER COMPLETE " + runId + " on " + instance);
            } catch (Exception e) {
                throw new JobExecutionException(e, false);
            }
        }
    }

    private static void scheduleRecovery() throws Exception {
        String runId = failoverId();
        ensureRecoveryLog();
        Scheduler s = scheduler();
        try {
            s.scheduleJob(JobBuilder.newJob(RecoveryJob.class)
                            .withIdentity("failover-" + runId, "poc")
                            .usingJobData("runId", runId)
                            .requestRecovery(true)
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity("failover-" + runId, "poc")
                            .startAt(Date.from(Instant.now().plusSeconds(2)))
                            .build());
            System.out.println("SCHEDULED recovery job for " + runId);
        } finally {
            s.shutdown();
        }
    }

    private static long recoveryCount(String runId, String phase) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, password());
             PreparedStatement s = c.prepareStatement("SELECT count(*) FROM quartz.poc_recovery_log WHERE run_id=? AND phase=?")) {
            s.setString(1, runId);
            s.setString(2, phase);
            try (ResultSet r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }

    private static void checkRecovery() throws Exception {
        String runId = failoverId();
        if (recoveryCount(runId, "START") != 1 || recoveryCount(runId, "RECOVER_START") != 1
                || recoveryCount(runId, "COMPLETE") != 1) {
            throw new IllegalStateException("Recovery counts must be START=1, RECOVER_START=1, COMPLETE=1 for " + runId);
        }
        try (Connection c = DriverManager.getConnection(URL, USER, password());
             PreparedStatement s = c.prepareStatement("SELECT count(DISTINCT scheduler_instance) FROM quartz.poc_recovery_log WHERE run_id=?")) {
            s.setString(1, runId);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                if (r.getLong(1) != 2) throw new IllegalStateException("Recovery must run on a different scheduler instance");
            }
        }
        System.out.println("PASS: failed-node recovery on a second instance for " + runId);
    }

    private static void recoveryStatus() throws Exception {
        String runId = failoverId();
        ensureRecoveryLog();
        long active;
        try (Connection c = DriverManager.getConnection(URL, USER, password());
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM quartz.qrtz_scheduler_state WHERE sched_name='P007Cluster'")) {
            r.next();
            active = r.getLong(1);
        }
        System.out.println("ACTIVE_NODE_COUNT=" + active);
        System.out.println("START=" + recoveryCount(runId, "START"));
        System.out.println("RECOVER_START=" + recoveryCount(runId, "RECOVER_START"));
        System.out.println("COMPLETE=" + recoveryCount(runId, "COMPLETE"));
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
            case "failover-schedule" -> scheduleRecovery();
            case "failover-node" -> {
                Scheduler s = scheduler();
                s.start();
                System.out.println("FAILOVER_NODE_READY instance=" + s.getSchedulerInstanceId());
                try {
                    String stopFile = System.getenv("P0_07_STOP_FILE");
                    long deadline = System.currentTimeMillis() + 100_000;
                    while (System.currentTimeMillis() < deadline) {
                        if (stopFile != null && Files.exists(Path.of(stopFile))) break;
                        Thread.sleep(500);
                    }
                } finally { s.shutdown(true); }
            }
            case "failover-check" -> checkRecovery();
            case "failover-status" -> recoveryStatus();
            default -> throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
    }
}
