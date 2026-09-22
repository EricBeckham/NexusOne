package one.nexus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.jdbc.JdbcTaskQueue;
import one.nexus.task.quartz.ExecutionRuntime;
import one.nexus.task.quartz.IsolatedJobScheduler;
import one.nexus.task.quartz.QuartzSchedulers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;

class QuartzJobStoreIT {
    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startDb() throws Exception {
        postgres = EmbeddedTaskSupport.start();
        EmbeddedTaskSupport.assertPostgres15(postgres);
        EmbeddedTaskSupport.migrate(postgres);
        dataSource = EmbeddedTaskSupport.dataSource(postgres);
    }

    @AfterEach
    void reset() {
        AccessContextHolder.clear();
        ExecutionRuntime.clear();
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("""
                    TRUNCATE
                      quartz.QRTZ_FIRED_TRIGGERS,
                      quartz.QRTZ_SIMPLE_TRIGGERS,
                      quartz.QRTZ_CRON_TRIGGERS,
                      quartz.QRTZ_SIMPROP_TRIGGERS,
                      quartz.QRTZ_BLOB_TRIGGERS,
                      quartz.QRTZ_TRIGGERS,
                      quartz.QRTZ_JOB_DETAILS,
                      quartz.QRTZ_CALENDARS,
                      quartz.QRTZ_PAUSED_TRIGGER_GRPS,
                      quartz.QRTZ_LOCKS,
                      quartz.QRTZ_SCHEDULER_STATE,
                      platform.exec_idempotency,
                      platform.exec_dead_letter,
                      platform.exec_job_log
                    """);
            return null;
        });
    }

    private RecordingHandler installRuntime() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        Clock clock = Clock.systemUTC();
        RecordingHandler handler = new RecordingHandler();
        ExecutionRuntime.install(new ExecutionRuntime(
                isolation,
                List.of(handler),
                new JdbcIdempotencyStore(dataSource, clock),
                new JdbcDeadLetterStore(dataSource, clock),
                new JdbcJobLogWriter(dataSource, clock)));
        return handler;
    }

    @Test
    void misfireFireNowRunsOnceWithRestoredTenant() throws Exception {
        RecordingHandler handler = installRuntime();
        ExecutionIsolation isolation = ExecutionRuntime.get().isolation();
        String url = EmbeddedTaskSupport.jdbcUrl(postgres);
        Scheduler scheduler = QuartzSchedulers.jdbc(url, "postgres", "postgres");
        IsolatedJobScheduler jobs = new IsolatedJobScheduler(scheduler, isolation);
        EmbeddedTaskSupport.bind(isolation, "alpha", "trace-misfire");
        jobs.scheduleOnce("misfire-job", Instant.now().minusSeconds(30), "quartz.ping", "{\"misfire\":true}");
        AccessContextHolder.clear();
        scheduler.start();
        try {
            waitUntil(() -> handler.seen.size() == 1, Duration.ofSeconds(15));
            assertEquals(List.of("alpha:{\"misfire\":true}"), handler.seen);
        } finally {
            scheduler.shutdown(true);
        }
    }

    @Test
    void clusteredJobStoreChecksInAndFiresEachJobOnce() throws Exception {
        RecordingHandler handler = installRuntime();
        ExecutionIsolation isolation = ExecutionRuntime.get().isolation();
        String url = EmbeddedTaskSupport.jdbcUrl(postgres);
        Scheduler node = QuartzSchedulers.jdbc(url, "postgres", "postgres");
        IsolatedJobScheduler jobs = new IsolatedJobScheduler(node, isolation);
        EmbeddedTaskSupport.bind(isolation, "alpha", "trace-cluster");
        for (int i = 0; i < 4; i++) {
            jobs.scheduleOnce("cluster-" + i, Instant.now().plusMillis(400), "quartz.ping", "n=" + i);
        }
        AccessContextHolder.clear();
        node.start();
        try {
            waitUntil(() -> handler.payloads.size() == 4, Duration.ofSeconds(20));
            assertEquals(Set.of("n=0", "n=1", "n=2", "n=3"), handler.payloads);
            assertEquals(4, handler.seen.size());
            Integer stateRows = Jdbc.call(dataSource, connection -> {
                try (var ps = connection.prepareStatement(
                        "SELECT count(*) FROM quartz.QRTZ_SCHEDULER_STATE WHERE SCHED_NAME = ?")) {
                    ps.setString(1, QuartzSchedulers.INSTANCE_NAME);
                    try (var rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            });
            assertTrue(stateRows >= 1, "clustered JobStore must check in, saw " + stateRows);
            assertTrue(node.getMetaData().isJobStoreClustered());
        } finally {
            node.shutdown(true);
        }
    }

    @Test
    void taskQueueAndQuartzShareIsolationKernel() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        JdbcTaskQueue queue = new JdbcTaskQueue(
                dataSource, isolation, ExecutionSettings.defaults(), Clock.systemUTC());
        EmbeddedTaskSupport.bind(isolation, "beta", "trace-shared");
        UUID id = queue.submit("export.csv", "{}");
        assertEquals("beta", queue.find(id).orElseThrow().tenantId());
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent("quartz.ping", "{}");
        assertEquals("beta", envelope.tenantId());
        assertEquals("trace-shared", envelope.traceId());
    }

    private static void waitUntil(Check check, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("timed out");
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static final class RecordingHandler implements TaskHandler {
        private final List<String> seen = new CopyOnWriteArrayList<>();
        private final Set<String> payloads = ConcurrentHashMap.newKeySet();

        @Override
        public boolean supports(String taskType) {
            return "quartz.ping".equals(taskType);
        }

        @Override
        public void execute(IsolatedEnvelope envelope) {
            seen.add(AccessContextHolder.current().boundTenantId().orElseThrow() + ":" + envelope.payload());
            payloads.add(envelope.payload());
        }
    }
}
