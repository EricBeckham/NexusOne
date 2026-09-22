package one.nexus.permission.redis;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PUBLISH-only accelerator. Lost messages or a down Redis must not leave a
 * process on a stale snapshot — SnapshotManager always re-reads the DB version.
 * This class does not implement Sentinel / replica failover.
 */
public final class RedisPolicyInvalidation implements PolicyInvalidationPort {
    private static final Logger log = LoggerFactory.getLogger(RedisPolicyInvalidation.class);
    private final Redis32Client client;
    private final Consumer<Exception> onFailure;

    public RedisPolicyInvalidation(Redis32Client client) {
        this(client, ex -> log.warn("redis invalidation publish failed; DB version poll remains authoritative: {}",
                ex.getMessage()));
    }

    public RedisPolicyInvalidation(Redis32Client client, Consumer<Exception> onFailure) {
        this.client = client;
        this.onFailure = onFailure;
    }

    @Override
    public void notifyTenant(String tenantId) {
        client.publishAndIgnoreFailure(PolicyCacheKeys.INVALIDATE_CHANNEL, tenantId, onFailure);
        try {
            client.setEx(PolicyCacheKeys.version(tenantId), "invalidate", 30);
        } catch (RuntimeException ex) {
            if (onFailure != null) {
                onFailure.accept(ex);
            }
        }
    }
}
