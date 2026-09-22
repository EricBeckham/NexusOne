package one.nexus.event;

/**
 * Database idempotency table. Critical-path keys must not live in cache (M0-10 Redis).
 */
public interface IdempotencyStore {
    enum Claim {
        FIRST,
        DUPLICATE
    }

    Claim claim(String scope, String key, String tenantId, String traceId);

    boolean exists(String scope, String key);

    default boolean first(String scope, String key, String tenantId, String traceId) {
        return claim(scope, key, tenantId, traceId) == Claim.FIRST;
    }
}
