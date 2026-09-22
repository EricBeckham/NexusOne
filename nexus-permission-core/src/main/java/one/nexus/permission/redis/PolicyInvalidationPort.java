package one.nexus.permission.redis;

/** Redis / in-process invalidation. Failure must not become the policy source of truth. */
public interface PolicyInvalidationPort {
    void notifyTenant(String tenantId);

    default void onRemoteInvalidation(String tenantId) {}
}
