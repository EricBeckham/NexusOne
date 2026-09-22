package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Port of the P0-02 SnapshotManager cases. */
class SnapshotManagerTest {
    @Test
    void snapshotPollDetectsVersionChangeWithoutNotification() {
        Map<String, Long> versions = new HashMap<>();
        versions.put("t1", 1L);
        List<Long> loaded = new ArrayList<>();
        SnapshotManager manager = new SnapshotManager(
                versions::get,
                tenant -> {
                    loaded.add(versions.get(tenant));
                    return new PolicySnapshot(tenant, versions.get(tenant));
                });
        assertEquals(1, manager.get("t1").version());
        versions.put("t1", 2L);
        assertEquals(2, manager.get("t1").version());
        manager.notifyInvalidated("t1");
        assertEquals(2, manager.get("t1").version());
        assertEquals(List.of(1L, 2L, 2L), loaded);
    }

    @Test
    void staleSnapshotOrMissingDbVersionFailsClosed() {
        SnapshotManager stale = new SnapshotManager(tenant -> 2L, tenant -> new PolicySnapshot("t1", 1));
        assertThrows(SnapshotUnavailableException.class, () -> stale.get("t1", true));
        SnapshotManager missing = new SnapshotManager(tenant -> {
            throw new IllegalStateException("db down");
        }, tenant -> new PolicySnapshot("t1", 1));
        assertThrows(SnapshotUnavailableException.class, () -> missing.get("t1"));
    }

    @Test
    void versionRegressionFailsClosed() {
        SnapshotManager manager = new SnapshotManager(tenant -> 1L, tenant -> new PolicySnapshot("t1", 3));
        assertThrows(SnapshotUnavailableException.class, () -> manager.get("t1"));
    }

    @Test
    void pollCachedReloadsWithoutACallerGet() {
        Map<String, Long> versions = new HashMap<>();
        versions.put("t1", 1L);
        List<Long> loaded = new ArrayList<>();
        SnapshotManager manager = new SnapshotManager(
                versions::get,
                tenant -> {
                    loaded.add(versions.get(tenant));
                    return new PolicySnapshot(tenant, versions.get(tenant));
                });
        assertEquals(1, manager.get("t1").version());
        versions.put("t1", 2L);
        assertEquals(1L, manager.cachedVersion("t1"));
        assertEquals(1, manager.pollCached());
        assertEquals(2L, manager.cachedVersion("t1"));
        assertEquals(List.of(1L, 2L), loaded);
    }

    @Test
    void pollCachedSwallowsLoadFailureSoBackgroundDoesNotFailClosed() {
        SnapshotManager manager = new SnapshotManager(
                tenant -> {
                    throw new IllegalStateException("db version unavailable");
                },
                tenant -> new PolicySnapshot("t1", 1));
        assertEquals(0, manager.pollCached());
    }

    @Test
    void hangingVersionReadRejectsAfterConfiguredTimeout() {
        CountDownLatch hang = new CountDownLatch(1);
        Duration timeout = Duration.ofMillis(150);
        SnapshotManager manager = new SnapshotManager(
                tenant -> awaitForever(hang),
                tenant -> new PolicySnapshot(tenant, 1),
                timeout);
        assertEquals(timeout, manager.loadTimeout());
        long started = System.nanoTime();
        SnapshotUnavailableException ex = assertThrows(SnapshotUnavailableException.class, () -> manager.get("t1"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        hang.countDown();
        assertTrue(ex.getMessage().contains("policy version timed out after 150ms"), ex.getMessage());
        assertTrue(elapsedMs >= 120, "elapsed=" + elapsedMs);
        assertTrue(elapsedMs < 2_000, "must reject near the timeout, not hang; elapsed=" + elapsedMs);
    }

    @Test
    void hangingSnapshotLoadRejectsAfterRemainingTimeout() {
        CountDownLatch hang = new CountDownLatch(1);
        SnapshotManager manager = new SnapshotManager(
                tenant -> 1L,
                tenant -> {
                    awaitForever(hang);
                    return new PolicySnapshot(tenant, 1);
                },
                Duration.ofMillis(150));
        long started = System.nanoTime();
        SnapshotUnavailableException ex = assertThrows(SnapshotUnavailableException.class, () -> manager.get("t1"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        hang.countDown();
        assertTrue(ex.getMessage().contains("policy snapshot timed out after 150ms"), ex.getMessage());
        assertTrue(elapsedMs >= 120, "elapsed=" + elapsedMs);
        assertTrue(elapsedMs < 2_000, "must reject near the timeout, not hang; elapsed=" + elapsedMs);
    }

    @Test
    void pollCachedSwallowsTimeoutSoBackgroundDoesNotFailClosed() {
        CountDownLatch hang = new CountDownLatch(1);
        AtomicBoolean block = new AtomicBoolean(false);
        SnapshotManager manager = new SnapshotManager(
                tenant -> {
                    if (block.get()) {
                        awaitForever(hang);
                    }
                    return 1L;
                },
                tenant -> new PolicySnapshot("t1", 1),
                Duration.ofMillis(80));
        assertEquals(1, manager.get("t1").version());
        block.set(true);
        assertEquals(0, manager.pollCached());
        hang.countDown();
    }

    private static Long awaitForever(CountDownLatch hang) {
        try {
            hang.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while hanging", ex);
        }
        return 1L;
    }
}
