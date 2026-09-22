package one.nexus.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * G3 leftover beyond submit-start keys: at-least-once Outbox delivery plus
 * {@code platform.exec_idempotency} skipping a second handler side effect.
 * Does not rebuild {@link OutboxDispatcher}. Not a product G3 close.
 */
class G3OutboxConsumeIdempotentTest {
    private static final int STORM = 32;
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
            connection.createStatement().execute(
                    "TRUNCATE platform.exec_outbox, platform.exec_idempotency, platform.exec_dead_letter, platform.exec_job_log");
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
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public boolean supports(String destination) {
                return OutboxAppendCommand.NOTIFY_INBOX.equals(destination);
            }

            @Override
            public void onDispatch(IsolatedEnvelope envelope) {
                handlerCalls.incrementAndGet();
            }
        };
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                store, isolation, List.of(handler), idempotency, dead, log, settings, clock, false);
        return new Harness(isolation, store, dispatcher, idempotency, handlerCalls);
    }

    private record Harness(
            ExecutionIsolation isolation,
            JdbcOutboxStore store,
            OutboxDispatcher dispatcher,
            JdbcIdempotencyStore idempotency,
            AtomicInteger handlerCalls
    ) {}

    @Test
    void firstDispatchDeliversAtLeastOnce() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-g3-once");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"n\":1}"));
        AccessContextHolder.clear();
        assertEquals(1, h.dispatcher.dispatchOnce());
        assertEquals(1, h.handlerCalls.get());
        assertEquals("DISPATCHED", h.store.find(id).orElseThrow().status());
        assertEquals(1, countKeys("outbox-dispatched"));
    }

    @Test
    void requeueStormSkipsHandlerAfterDbKey() {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-g3-storm");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"storm\":true}"));
        assertEquals(1, h.dispatcher.dispatchOnce());
        assertEquals(1, h.handlerCalls.get());
        for (int i = 0; i < STORM; i++) {
            h.store.requeue(id);
            assertEquals(1, h.dispatcher.dispatchOnce(), "replay " + i + " must still mark delivered");
        }
        assertEquals(1, h.handlerCalls.get(), "DB outbox-dispatched key must skip handler side effects");
        assertEquals("DISPATCHED", h.store.find(id).orElseThrow().status());
        assertEquals(1, countKeys("outbox-dispatched"));
        assertEquals(1, countOutboxRows(id));
    }

    @Test
    void concurrentReplaysAfterFirstClaimSkipHandler() throws Exception {
        Harness h = harness();
        EmbeddedExecutionSupport.bind(h.isolation, "alpha", "trace-g3-race");
        UUID id = h.store.append(OutboxAppendCommand.notifyInbox("notify.created", "{\"race\":true}"));
        assertEquals(1, h.dispatcher.dispatchOnce());
        OutboxRecord delivered = h.store.find(id).orElseThrow();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger deliveredFlags = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (h.dispatcher.dispatchOne(delivered)) {
                            deliveredFlags.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, h.handlerCalls.get());
        assertEquals(threads, deliveredFlags.get());
        assertEquals(1, countKeys("outbox-dispatched"));
    }

    @Test
    void concurrentDbKeyClaimsOnlyOneFirst() throws Exception {
        Harness h = harness();
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        IdempotencyStore.Claim claim = h.idempotency.claim(
                                "outbox-dispatched",
                                "g3-shared-key",
                                "alpha",
                                "trace-g3-claim");
                        if (claim == IdempotencyStore.Claim.FIRST) {
                            first.incrementAndGet();
                        } else {
                            duplicate.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, first.get());
        assertEquals(threads - 1, duplicate.get());
        assertTrue(h.idempotency.exists("outbox-dispatched", "g3-shared-key"));
        assertEquals(1, countKeys("outbox-dispatched"));
    }

    @Test
    void handlerClaimSkipsSecondSideEffectOnDuplicateDelivery() {
        ExecutionIsolation isolation = ExecutionIsolation.create();
        Clock clock = Clock.systemUTC();
        JdbcIdempotencyStore idempotency = new JdbcIdempotencyStore(dataSource, clock);
        AtomicInteger sideEffects = new AtomicInteger();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public boolean supports(String destination) {
                return OutboxAppendCommand.NOTIFY_INBOX.equals(destination);
            }

            @Override
            public void onDispatch(IsolatedEnvelope envelope) {
                if (idempotency.claim(
                        OutboxAppendCommand.NOTIFY_INBOX,
                        envelope.traceId() + ":" + envelope.payload(),
                        envelope.tenantId(),
                        envelope.traceId()) == IdempotencyStore.Claim.DUPLICATE) {
                    return;
                }
                sideEffects.incrementAndGet();
            }
        };
        EmbeddedExecutionSupport.bind(isolation, "alpha", "trace-g3-handler");
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(
                OutboxAppendCommand.NOTIFY_INBOX, "{\"n\":1}");
        handler.onDispatch(envelope);
        handler.onDispatch(envelope);
        handler.onDispatch(envelope);
        assertEquals(1, sideEffects.get());
        assertEquals(1, countKeys(OutboxAppendCommand.NOTIFY_INBOX));
    }

    private static int countKeys(String scope) {
        return Jdbc.call(dataSource, connection -> {
            try (var ps = connection.prepareStatement(
                    "SELECT count(*) FROM platform.exec_idempotency WHERE scope = ?")) {
                ps.setString(1, scope);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    private static int countOutboxRows(UUID id) {
        return Jdbc.call(dataSource, connection -> {
            try (var ps = connection.prepareStatement(
                    "SELECT count(*) FROM platform.exec_outbox WHERE id = ?")) {
                ps.setObject(1, id);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }
}
