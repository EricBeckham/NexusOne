package one.nexus.permission.redis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invalidate-only accelerator. PUBLISH + optional SUBSCRIBE never store a
 * PolicySnapshot body. Lost messages or a down Redis must not leave a process
 * on a stale snapshot — SnapshotManager always re-reads the DB version, and
 * {@code PolicySnapshotPoller} keeps doing that between requests. This class
 * does not implement Sentinel / replica failover.
 */
public final class RedisPolicyInvalidation implements PolicyInvalidationPort {
    private static final Logger log = LoggerFactory.getLogger(RedisPolicyInvalidation.class);
    private final Redis32Client client;
    private final Consumer<Exception> onFailure;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
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
        client.publishAndIgnoreFailure(PolicyCacheKeys.INVALIDATE_CHANNEL, tenantId, onFailure);
        try {
            client.setEx(PolicyCacheKeys.version(tenantId), "invalidate", 30);
        } catch (RuntimeException ex) {
            if (onFailure != null) {
                onFailure.accept(ex);
            }
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
