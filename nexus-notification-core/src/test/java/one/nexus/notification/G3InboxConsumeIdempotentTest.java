package one.nexus.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.cache.IsolationSupport;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.notification.infra.InMemoryNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G3 leftover: {@link InAppDeliveryHandler} honors {@code notify.inbox} DB-shaped
 * keys so a duplicate at-least-once delivery does not write the inbox again.
 * Does not rebuild Outbox / Dispatcher. Not a product G3 close.
 */
class G3InboxConsumeIdempotentTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private static final String TRACE = "trace-g3-inbox";
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("notify.ops"), new CollectingAuditSink());

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void duplicateOnDispatchSkipsSecondInboxUpdate() {
        IsolationSupport.bindTenant(kernel, TENANT, "user-a", "mem-a", TRACE);
        CountingRepository repo = new CountingRepository();
        Notification pending = pendingRow();
        repo.insert(pending);
        InAppDeliveryHandler handler = new InAppDeliveryHandler(repo, new MemoryIdempotencyStore());
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(
                OutboxAppendCommand.NOTIFY_INBOX,
                "{\"notification_id\":\"" + pending.id() + "\"}");
        handler.onDispatch(envelope);
        handler.onDispatch(envelope);
        handler.onDispatch(envelope);
        assertEquals(1, repo.updates.get());
        assertEquals(NotificationStatus.DELIVERED, repo.find(pending.id()).orElseThrow().status());
        assertEquals(1, repo.rows());
    }

    @Test
    void concurrentDuplicateDeliveryWritesInboxOnce() throws Exception {
        IsolationSupport.bindTenant(kernel, TENANT, "user-a", "mem-a", TRACE);
        CountingRepository repo = new CountingRepository();
        Notification pending = pendingRow();
        repo.insert(pending);
        InAppDeliveryHandler handler = new InAppDeliveryHandler(repo, new MemoryIdempotencyStore());
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(
                OutboxAppendCommand.NOTIFY_INBOX,
                "{\"notification_id\":\"" + pending.id() + "\"}");
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        IsolationSupport.bindTenant(kernel, TENANT, "user-a", "mem-a", TRACE);
                        handler.onDispatch(envelope);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        AccessContextHolder.clear();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, repo.updates.get());
        assertEquals(NotificationStatus.DELIVERED, repo.find(pending.id()).orElseThrow().status());
        assertEquals(1, repo.rows());
    }

    private static Notification pendingRow() {
        return new Notification(
                UUID.fromString("0199b500-0000-7000-8000-000000003301"),
                UUID.fromString(TENANT),
                "user-a",
                "approval.task.assigned",
                "待审批",
                "body",
                "{}",
                NotificationStatus.PENDING,
                null,
                Instant.parse("2026-09-22T18:00:00Z"),
                null);
    }

    private static final class CountingRepository implements NotificationRepository {
        private final InMemoryNotificationRepository delegate = new InMemoryNotificationRepository();
        private final AtomicInteger updates = new AtomicInteger();

        @Override
        public void insert(Notification notification) {
            delegate.insert(notification);
        }

        @Override
        public void update(Notification notification) {
            updates.incrementAndGet();
            delegate.update(notification);
        }

        @Override
        public java.util.Optional<Notification> find(UUID id) {
            return delegate.find(id);
        }

        @Override
        public java.util.List<Notification> listByRecipient(UUID tenantId, String recipientSubject) {
            return delegate.listByRecipient(tenantId, recipientSubject);
        }

        int rows() {
            return delegate.listByRecipient(UUID.fromString(TENANT), "user-a").size();
        }
    }

    /**
     * Same unique-insert shape as {@code platform.exec_idempotency}
     * {@code ON CONFLICT (scope, idempotency_key) DO NOTHING}.
     */
    private static final class MemoryIdempotencyStore implements IdempotencyStore {
        private final ConcurrentHashMap<String, Boolean> keys = new ConcurrentHashMap<>();

        @Override
        public Claim claim(String scope, String key, String tenantId, String traceId) {
            return keys.putIfAbsent(scope + "\0" + key, Boolean.TRUE) == null
                    ? Claim.FIRST
                    : Claim.DUPLICATE;
        }

        @Override
        public boolean exists(String scope, String key) {
            return keys.containsKey(scope + "\0" + key);
        }
    }
}
