package one.nexus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.task.jdbc.JdbcTaskQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TaskQueueContractTest {
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
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("TRUNCATE platform.exec_task, platform.exec_idempotency, platform.exec_dead_letter, platform.exec_job_log");
            return null;
        });
    }

    private Fixture fixture() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        ExecutionSettings settings = new ExecutionSettings(Duration.ofMillis(50), Duration.ofSeconds(5), Duration.ofSeconds(30), 8, 2);
        Clock clock = Clock.systemUTC();
        JdbcTaskQueue queue = new JdbcTaskQueue(dataSource, isolation, settings, clock);
        List<String> seen = new ArrayList<>();
        TaskHandler handler = new TaskHandler() {
            @Override
            public boolean supports(String taskType) {
                return "export.csv".equals(taskType);
            }

            @Override
            public void execute(IsolatedEnvelope envelope) {
                seen.add(AccessContextHolder.current().boundTenantId().orElseThrow() + ":" + envelope.payload());
            }
        };
        TaskWorker worker = new TaskWorker(
                queue, isolation, List.of(handler),
                new JdbcIdempotencyStore(dataSource, clock),
                new JdbcDeadLetterStore(dataSource, clock),
                new JdbcJobLogWriter(dataSource, clock),
                settings, clock, false);
        return new Fixture(isolation, queue, worker, seen);
    }

    private record Fixture(ExecutionIsolation isolation, JdbcTaskQueue queue, TaskWorker worker, List<String> seen) {}

    @Test
    void submitWithoutContextIsRejected() {
        Fixture f = fixture();
        IsolationException ex = assertThrows(IsolationException.class, () -> f.queue.submit("export.csv", "{}"));
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
    }

    @Test
    void workerRestoresTenantFromStoredMembership() {
        Fixture f = fixture();
        EmbeddedTaskSupport.bind(f.isolation, "alpha", "trace-task-1");
        UUID id = f.queue.submit("export.csv", "{\"rows\":2}");
        AccessContextHolder.clear();
        assertEquals(1, f.worker.workOnce());
        assertEquals(List.of("alpha:{\"rows\":2}"), f.seen);
        assertEquals("SUCCEEDED", f.queue.find(id).orElseThrow().status());
    }

    @Test
    void crossTenantPayloadRejectedBeforeHandler() {
        Fixture f = fixture();
        EmbeddedTaskSupport.bind(f.isolation, "alpha", "trace-a");
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent("export.csv", "{}");
        EmbeddedTaskSupport.bind(f.isolation, "beta", "trace-b");
        var ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () -> envelope.dispatch(item -> ran.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, ex.error());
        assertFalse(ran.get());
    }

    @Test
    void failedTaskRetriesThenDeadLetters() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        ExecutionSettings settings = new ExecutionSettings(Duration.ofMillis(50), Duration.ofSeconds(5), Duration.ofSeconds(30), 8, 2);
        Clock clock = Clock.systemUTC();
        JdbcTaskQueue queue = new JdbcTaskQueue(dataSource, isolation, settings, clock);
        JdbcDeadLetterStore dead = new JdbcDeadLetterStore(dataSource, clock);
        TaskHandler boom = new TaskHandler() {
            @Override
            public boolean supports(String taskType) {
                return "explode".equals(taskType);
            }

            @Override
            public void execute(IsolatedEnvelope envelope) {
                throw new IllegalStateException("nope");
            }
        };
        TaskWorker worker = new TaskWorker(
                queue, isolation, List.of(boom),
                new JdbcIdempotencyStore(dataSource, clock),
                dead, new JdbcJobLogWriter(dataSource, clock), settings, clock, false);
        EmbeddedTaskSupport.bind(isolation, "alpha", "trace-explode");
        UUID id = queue.submit("explode", "{}");
        assertEquals(0, worker.workOnce());
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("UPDATE platform.exec_task SET available_at = now() - interval '1 second'");
            return null;
        });
        assertEquals(0, worker.workOnce());
        assertEquals("DEAD", queue.find(id).orElseThrow().status());
        assertEquals(1, dead.list("TASK", 10).size());
        assertTrue(dead.list("TASK", 1).getFirst().tenantId().equals("alpha"));
    }
}
