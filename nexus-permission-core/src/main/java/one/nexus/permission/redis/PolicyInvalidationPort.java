package one.nexus.permission.redis;

import java.util.function.Consumer;

/**
 * Redis / in-process invalidation. Failure must not become the policy source of
 * truth. Implementations fan out to {@link #addListener} so each instance's
 * {@code SnapshotManager} can drop its LRU entry; they never store a snapshot
 * body in Redis.
 */
public interface PolicyInvalidationPort {
    void notifyTenant(String tenantId);

    default void addListener(Consumer<String> listener) {}

    /** Start a Redis SUBSCRIBE loop. No-op for in-process ports. */
    default void startListening() {}

    default void shutdown() {}

    default void onRemoteInvalidation(String tenantId) {}
}
