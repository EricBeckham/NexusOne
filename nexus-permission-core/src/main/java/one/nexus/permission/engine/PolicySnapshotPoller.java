package one.nexus.permission.engine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short-cycle DB version poll. Redis invalidate only accelerates; this loop is
 * why a lost pub/sub message cannot leave a process on an old snapshot forever.
 * Does not implement Redis Sentinel / Cluster failover or a P2-down timeout.
 */
public final class PolicySnapshotPoller implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PolicySnapshotPoller.class);

    private final SnapshotManager snapshots;
    private final Duration interval;
    private final ScheduledExecutorService executor;

    public PolicySnapshotPoller(SnapshotManager snapshots, Duration interval) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.interval = interval == null ? Duration.ZERO : interval;
        this.executor = Executors.newSingleThreadScheduledExecutor(thread -> {
            Thread worker = new Thread(thread, "nexus-policy-snapshot-poll");
            worker.setDaemon(true);
            return worker;
        });
    }

    public Duration interval() {
        return interval;
    }

    /** One poll of every cached tenant. Safe to call from tests without {@link #start()}. */
    public int tick() {
        return snapshots.pollCached();
    }

    public void start() {
        if (interval.isZero() || interval.isNegative()) {
            return;
        }
        long millis = Math.max(1L, interval.toMillis());
        executor.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (RuntimeException ex) {
                log.warn("policy snapshot poller tick failed: {}", ex.getMessage());
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
