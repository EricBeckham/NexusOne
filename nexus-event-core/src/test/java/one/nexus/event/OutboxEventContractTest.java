package one.nexus.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import one.nexus.common.event.DomainEvent;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.event.jdbc.JdbcReconciliation;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExecutor;
import one.nexus.isolation.context.AccessContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OutboxEventContractTest {
    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startDb() throws Exception {
        postgres = EmbeddedExecutionSupport.start();
        EmbeddedExecutionSupport.assertPostgres15(postgres);
        EmbeddedExecutionSupport.migrate(postgres);
        dataSource = EmbeddedExecutionSupport.dataSource(postgres);
    }

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("TRUNCATE platform.exec_outbox, platform.exec_idempotency, platform.exec_dead_letter, platform.exec_job_log");
            return null;
        });
    }

    private Harness harness() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        ExecutionSettings settings = new ExecutionSettings(
                java.time.Duration.ofMillis(50),
                java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(30),
                8,
                2);
        Clock clock = Clock.systemUTC();
        JdbcOutboxStore store = new JdbcOutboxStore(dataSource, isolation, settings, clock);
        JdbcIdempotencyStore idempotency = new JdbcIdempotencyStore(dataSource, clock);
        JdbcDeadLetterStore dead = new JdbcDeadLetterStore(dataSource, clock);
        JdbcJobLogWriter log = new JdbcJobLogWriter(dataSource, clock);
        List<String> seen = new ArrayList<>();
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public boolean supports(String destination) {
                return OutboxAppendCommand.NOTIFY_INBOX.equals(destination) || "test.echo".equals(destination);
            }

            @Override
            public void onDispatch(IsolatedEnvelope envelope) {
                handlerCalls.incrementAndGet();
                seen.add(AccessContextHolder.current().boundTenantId().orElseThrow() + ":" + envelope.payload());
            }
        };
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                store, isolation, List.of(handler), idempotency, dead, log, settings, clock, false);
        return new Harness(isolation, store, dispatcher, dead, new JdbcReconciliation(store, dataSource, dead, clock), seen, handlerCalls);
    }

    private record Harness(
            ExecutionIsolation isolation,
            JdbcOutboxStore store,
            OutboxDispatcher dispatcher,
            JdbcDeadLetterStore dead,
            ReconciliationPort reconcile,
            List<String> seen,
            AtomicInteger handlerCalls
    ) {}

    @Test
    void appendRequiresBoundM002Context() {
        Harness h = harness();
        IsolationException ex = assertThrows(IsolationException.class,
                () -> h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{}")));
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
    }

    @Test
    void isolatedExecutorRejectsSubmitWithoutContext() {
        var ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () -> IsolatedExecutor.wrap(() -> ran.set(true)));
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
        assertFalse(ran.get());
    }

    @Test
    void dispatchRestoresTenantAndDeliversAtLeastOnce() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-alpha-1");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"ok\":true}"));
        AccessContextHolder.clear();
        assertEquals(1, h.dispatcher.dispatchOnce());
        assertEquals(List.of("alpha:{\"ok\":true}"), h.seen);
        assertEquals("DISPATCHED", h.store.find(id).orElseThrow().status());
    }

    @Test
    void crossTenantEnvelopeRejectedBeforeHandler() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-a");
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(OutboxAppendCommand.NOTIFY_INBOX, "{}");
        EmbeddedExecutionSupport.bind(h.isolation, "beta", "trace-b");
        var ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () -> envelope.dispatch(item -> ran.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, ex.error());
        assertFalse(ran.get());
    }

    @Test
    void publishRejectsDomainEventForOtherTenant() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-a");
        DomainEvent foreign = new DomainEvent(
                UUID.fromString("0199b500-0000-7000-8000-0000000000aa"),
                "ledger.created",
                1,
                UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                "ledger",
                UUID.fromString("0199b500-0000-7000-8000-0000000000bb"),
                DomainEvent.now(),
                "p3",
                null,
                "trace-a",
                null,
                null);
        IsolationException ex = assertThrows(IsolationException.class, () -> h.store.publish(foreign, "test.echo", "{}"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, ex.error());
    }

    @Test
    void retryThenDeadLetterAndReplay() {
        Harness h = harness();
        ExecutionIsolation isolation = h.isolation;
        ExecutionSettings settings = new ExecutionSettings(
                java.time.Duration.ofMillis(50), java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(30), 8, 2);
        AtomicInteger blows = new AtomicInteger();
        OutboxHandler exploding = new OutboxHandler() {
            @Override
            public boolean supports(String destination) {
                return "test.boom".equals(destination);
            }

            @Override
            public void onDispatch(IsolatedEnvelope envelope) {
                blows.incrementAndGet();
                throw new IllegalStateException("boom");
            }
        };
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                h.store, isolation, List.of(exploding),
                new JdbcIdempotencyStore(dataSource, Clock.systemUTC()),
                h.dead, new JdbcJobLogWriter(dataSource, Clock.systemUTC()),
                settings, Clock.systemUTC(), false);
        EmbeddedExecutionSupport.bind(isolation, "alpha", "trace-boom");
        UUID id = h.store.append(new OutboxAppendCommand("test.boom", "boom.v1", 1, "{}", null, null, null, null));
        assertEquals(0, dispatcher.dispatchOnce());
        assertEquals("FAILED", h.store.find(id).orElseThrow().status());
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("UPDATE platform.exec_outbox SET available_at = now() - interval '1 second' WHERE id = '" + id + "'");
            return null;
        });
        assertEquals(0, dispatcher.dispatchOnce());
        assertEquals("DEAD", h.store.find(id).orElseThrow().status());
        assertEquals(1, h.dead.list("OUTBOX", 10).size());
        assertTrue(h.reconcile.replayDead(h.dead.list("OUTBOX", 1).getFirst().id()));
        assertEquals("PENDING", h.store.find(id).orElseThrow().status());
        assertEquals(2, blows.get());
    }

    @Test
    void idempotentReconsumeSkipsHandlerSideEffect() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-idem");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"n\":1}"));
        assertEquals(1, h.dispatcher.dispatchOnce());
        h.store.requeue(id);
        assertEquals(1, h.dispatcher.dispatchOnce());
        assertEquals(1, h.handlerCalls.get());
    }

    @Test
    void lagProbeAlarmsWhenOldestPendingExceeds30s() {
        DeliveryLagProbe probe = new DeliveryLagProbe(java.time.Duration.ofSeconds(30));
        assertTrue(probe.warnIfLagging(java.util.Optional.of(Instant.now().minusSeconds(40)), Instant.now()));
        assertTrue(probe.lastAlarmAge().orElseThrow().toSeconds() >= 30);
        assertFalse(probe.warnIfLagging(java.util.Optional.of(Instant.now().minusSeconds(2)), Instant.now()));
        assertTrue(probe.lastAlarmAge().isEmpty());
        for (int i = 0; i < 20; i++) {
            probe.recordStartOfDelivery(Instant.now().minusMillis(12), Instant.now());
        }
        assertTrue(probe.p95Ms().orElseThrow() <= DeliveryLagProbe.TARGET_P95_MS);
        assertEquals(20, probe.sampleCount());
    }

    @Test
    void dispatchOnceAlarmsWhenOldestPendingOrFailedExceeds30s() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-lag");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"lag\":true}"));
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("""
                    UPDATE platform.exec_outbox
                    SET created_at = now() - interval '40 seconds',
                        available_at = now() + interval '1 hour'
                    WHERE id = '%s'
                    """.formatted(id));
            return null;
        });
        assertEquals(0, h.dispatcher.dispatchOnce());
        assertTrue(h.dispatcher.lastCycleLagAlarm());
        assertEquals("PENDING", h.store.find(id).orElseThrow().status());
        assertTrue(h.dispatcher.lagProbe().lastAlarmAge().orElseThrow().toSeconds() >= 30);
    }

    @Test
    void startOfDeliveryP95StaysUnder5sOnEmbeddedPg15() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-p95");
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(h.store.append(OutboxAppendCommand.notifyInbox(
                    "notify.created", "{\"n\":" + i + "}")));
        }
        assertEquals(8, h.dispatcher.dispatchOnce());
        assertEquals(8, h.dispatcher.dispatchOnce());
        assertEquals(4, h.dispatcher.dispatchOnce());
        assertFalse(h.dispatcher.lastCycleLagAlarm());
        long p95 = h.dispatcher.lagProbe().p95Ms().orElseThrow();
        assertTrue(p95 <= DeliveryLagProbe.TARGET_P95_MS,
                "start-of-delivery P95 " + p95 + "ms exceeds 5000ms (Zonky PG 15, in-process)");
        for (UUID id : ids) {
            assertEquals("DISPATCHED", h.store.find(id).orElseThrow().status());
        }
    }

    @Test
    void pollerDeliversNotifyInboxWithoutCallingDispatchOnce() throws Exception {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-poll");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"poll\":true}"));
        h.dispatcher.start();
        try {
            Instant deadline = Instant.now().plusSeconds(5);
            String status = "PENDING";
            while (Instant.now().isBefore(deadline)) {
                status = h.store.find(id).orElseThrow().status();
                if ("DISPATCHED".equals(status)) {
                    break;
                }
                Thread.sleep(20);
            }
            assertEquals("DISPATCHED", status);
            assertEquals(List.of("alpha:{\"poll\":true}"), h.seen);
        } finally {
            h.dispatcher.stop();
        }
    }

    @Test
    void reclaimExpiredDispatchingLease() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-lease");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{}"));
        Jdbc.run(dataSource, connection -> {
            connection.createStatement().execute("""
                    UPDATE platform.exec_outbox
                    SET status = 'DISPATCHING', lease_until = now() - interval '1 second'
                    WHERE id = '%s'
                    """.formatted(id));
            return null;
        });
        assertEquals(1, h.reconcile.reclaimExpiredLeases());
        assertEquals("FAILED", h.store.find(id).orElseThrow().status());
    }

    @Test
    void appendJoinsSpringTransactionAndRollsBack() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-tx");
        org.springframework.jdbc.support.JdbcTransactionManager tm =
                new org.springframework.jdbc.support.JdbcTransactionManager(dataSource);
        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(tm);
        assertThrows(IllegalStateException.class, () -> template.execute(status -> {
            h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"tx\":true}"));
            throw new IllegalStateException("force rollback");
        }));
        Integer count = Jdbc.call(dataSource, connection -> {
            try (var rs = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM platform.exec_outbox WHERE destination = 'notify.inbox' AND trace_id = 'trace-tx'")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertEquals(0, count);
    }
}
