package one.nexus.permission.redis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invalidate-only accelerator. PUBLISH + optional SUBSCRIBE never store a
 * PolicySnapshot body. Lost messages or a down Redis must not leave a process
 * on a stale snapshot — SnapshotManager always re-reads the DB version, and
 * {@code PolicySnapshotPoller} keeps doing that between requests. A failed
 * PUBLISH / SET EX is recorded as a remote failure, not as success; local
 * listener delivery is not a Redis success. This class does not implement
 * Sentinel / replica failover.
 */
public final class RedisPolicyInvalidation implements PolicyInvalidationPort {
    private static final Logger log = LoggerFactory.getLogger(RedisPolicyInvalidation.class);
    private final Redis32Client client;
    private final Consumer<Exception> onFailure;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger remoteNotifyFailureCount = new AtomicInteger();
    private final AtomicReference<RemoteNotifyOutcome> lastRemoteNotify = new AtomicReference<>();
    private Thread subscriber;

    public RedisPolicyInvalidation(Redis32Client client) {
        this(client, ex -> log.warn("redis invalidation publish failed; DB version poll remains authoritative: {}",
                ex.getMessage()));
    }

    public RedisPolicyInvalidation(Redis32Client client, Consumer<Exception> onFailure) {
        this.client = client;
        this.onFailure = onFailure;
    }

    @Override
    public void addListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public synchronized void startListening() {
        if (subscriber != null && subscriber.isAlive()) {
            return;
        }
        running.set(true);
        subscriber = new Thread(
                () -> client.subscribeLoop(PolicyCacheKeys.INVALIDATE_CHANNEL, this::deliver, running),
                "nexus-policy-invalidate");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    @Override
    public synchronized void shutdown() {
        running.set(false);
        if (subscriber != null) {
            subscriber.interrupt();
            subscriber = null;
        }
    }

    @Override
    public void notifyTenant(String tenantId) {
        deliver(tenantId);
        boolean published = false;
        boolean versionHintWritten = false;
        int failures = 0;
        try {
            client.publish(PolicyCacheKeys.INVALIDATE_CHANNEL, tenantId);
            published = true;
        } catch (RuntimeException ex) {
            failures++;
            observeFailure(ex);
        }
        try {
            client.setEx(PolicyCacheKeys.version(tenantId), "invalidate", 30);
            versionHintWritten = true;
        } catch (RuntimeException ex) {
            failures++;
            observeFailure(ex);
        }
        remoteNotifyFailureCount.addAndGet(failures);
        lastRemoteNotify.set(new RemoteNotifyOutcome(published, versionHintWritten, failures));
    }

    /**
     * Last Redis PUBLISH + version-hint attempt. Local listener delivery is not
     * counted as success. {@code null} until the first {@link #notifyTenant}.
     */
    public RemoteNotifyOutcome lastRemoteNotify() {
        return lastRemoteNotify.get();
    }

    /** False when Redis PUBLISH or SET EX failed, or when nothing was published yet. */
    public boolean lastRemoteNotifySucceeded() {
        RemoteNotifyOutcome last = lastRemoteNotify.get();
        return last != null && last.succeeded();
    }

    public int remoteNotifyFailureCount() {
        return remoteNotifyFailureCount.get();
    }

    private void observeFailure(RuntimeException ex) {
        if (onFailure != null) {
            onFailure.accept(ex);
        }
    }

    /**
     * Remote (Redis) side of one {@link #notifyTenant}. Local LRU mark is
     * independent and is never treated as a Redis success.
     */
    public record RemoteNotifyOutcome(boolean published, boolean versionHintWritten, int failureCount) {
        public boolean succeeded() {
            return published && versionHintWritten && failureCount == 0;
        }
    }

    @Override
    public void onRemoteInvalidation(String tenantId) {
        deliver(tenantId);
    }

    public int listenerCount() {
        return listeners.size();
    }

    private void deliver(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(tenantId);
            } catch (RuntimeException ex) {
                log.warn("policy invalidate listener failed for {}: {}", tenantId, ex.getMessage());
            }
        }
    }
}
