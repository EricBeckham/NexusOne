package one.nexus.permission.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.SnapshotUnavailableException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RedisInvalidationTest {
    @Test
    void publishUsesP005KeyContractAndNotifyReloads() {
        Redis32Client client = tryClient();
        Assumptions.assumeTrue(client != null, "Redis 3.2 is not listening on 127.0.0.1:6379");
        assertEquals("PONG", client.ping());
        String tenant = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
        assertTrue(PolicyCacheKeys.snapshot(tenant).startsWith("nexus:v1:{"));
        assertTrue(PolicyCacheKeys.snapshot(tenant).contains("cache:permission:snapshot"));

        Map<String, Long> versions = new HashMap<>();
        versions.put(tenant, 1L);
        List<Long> loaded = new ArrayList<>();
        SnapshotManager manager = new SnapshotManager(versions::get, id -> {
            loaded.add(versions.get(id));
            return new PolicySnapshot(id, versions.get(id));
        });
        RedisPolicyInvalidation invalidation = new RedisPolicyInvalidation(client);
        invalidation.addListener(manager::notifyInvalidated);
        assertEquals(1, manager.get(tenant).version());
        versions.put(tenant, 2L);
        invalidation.notifyTenant(tenant);
        assertTrue(invalidation.lastRemoteNotifySucceeded(), "live Redis PUBLISH+SET EX is a remote success");
        assertTrue(manager.isMarkedInvalidated(tenant));
        assertEquals(2, manager.get(tenant).version());
        assertEquals(List.of(1L, 2L), loaded);
        assertTrue(client.get(PolicyCacheKeys.snapshot(tenant)).isEmpty(),
                "Redis must stay invalidate-only; snapshot body is never written");

        client.setEx(PolicyCacheKeys.version(tenant), "2", 5);
        assertEquals("2", client.get(PolicyCacheKeys.version(tenant)).orElseThrow());
        client.del(PolicyCacheKeys.version(tenant));
        assertTrue(client.get(PolicyCacheKeys.version(tenant)).isEmpty());
        client.del(PolicyCacheKeys.snapshot(tenant));
    }

    @Test
    void redisFailureDoesNotReplaceDbVersionAndDbFailureStillFailsClosed() {
        Redis32Client dead = new Redis32Client("127.0.0.1", 1);
        AtomicInteger failures = new AtomicInteger();
        RedisPolicyInvalidation invalidation = new RedisPolicyInvalidation(dead, ex -> failures.incrementAndGet());
        invalidation.notifyTenant("t1");
        assertTrue(failures.get() >= 1, "publish failure must be observed, not treated as success");
        assertFalse(invalidation.lastRemoteNotifySucceeded(),
                "Redis-down notify must not be recorded as a remote success");
        RedisPolicyInvalidation.RemoteNotifyOutcome last = invalidation.lastRemoteNotify();
        assertTrue(last != null && !last.published() && !last.versionHintWritten());
        assertTrue(invalidation.remoteNotifyFailureCount() >= 1);

        SnapshotManager manager = new SnapshotManager(tenant -> {
            throw new IllegalStateException("db version unavailable");
        }, tenant -> new PolicySnapshot("t1", 1));
        assertThrows(SnapshotUnavailableException.class, () -> manager.get("t1"));
    }

    @Test
    void subscribeOnceSeesPublish() throws Exception {
        Redis32Client client = tryClient();
        Assumptions.assumeTrue(client != null, "Redis 3.2 is not listening on 127.0.0.1:6379");
        Thread publisher = new Thread(() -> {
            try {
                Thread.sleep(150);
                client.publish(PolicyCacheKeys.INVALIDATE_CHANNEL, "t-notify");
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        publisher.start();
        var payload = client.subscribeOnce(PolicyCacheKeys.INVALIDATE_CHANNEL, Duration.ofSeconds(2));
        publisher.join(3000);
        assertEquals("t-notify", payload.orElse(""));
    }

    @Test
    void startListeningDeliversPublishToSnapshotManager() throws Exception {
        Redis32Client publisher = tryClient();
        Assumptions.assumeTrue(publisher != null, "Redis 3.2 is not listening on 127.0.0.1:6379");
        Redis32Client subscriber = tryClient();
        Assumptions.assumeTrue(subscriber != null, "Redis 3.2 is not listening on 127.0.0.1:6379");
        String tenant = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
        Map<String, Long> versions = new HashMap<>();
        versions.put(tenant, 1L);
        SnapshotManager manager = new SnapshotManager(versions::get, id -> new PolicySnapshot(id, versions.get(id)));
        RedisPolicyInvalidation invalidation = new RedisPolicyInvalidation(subscriber);
        invalidation.addListener(manager::notifyInvalidated);
        invalidation.startListening();
        try {
            assertEquals(1, manager.get(tenant).version());
            versions.put(tenant, 2L);
            Thread.sleep(150);
            publisher.publish(PolicyCacheKeys.INVALIDATE_CHANNEL, tenant);
            boolean marked = false;
            for (int i = 0; i < 40 && !marked; i++) {
                marked = manager.isMarkedInvalidated(tenant);
                if (!marked) {
                    Thread.sleep(50);
                }
            }
            assertTrue(marked, "SUBSCRIBE must mark the snapshot invalidated");
            assertEquals(2, manager.get(tenant).version());
        } finally {
            invalidation.shutdown();
        }
    }

    private static Redis32Client tryClient() {
        try {
            Redis32Client client = new Redis32Client("127.0.0.1", 6379);
            if (!"PONG".equals(client.ping())) {
                return null;
            }
            return client;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
