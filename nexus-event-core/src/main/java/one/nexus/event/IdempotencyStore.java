package one.nexus.event;

import java.util.Optional;

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

    /**
     * Same unique insert as {@link #claim}, but joins the ambient Spring transaction
     * so a business rollback (G18 / G3 submit-start) also drops the key.
     */
    default Claim claimInTransaction(String scope, String key, String tenantId, String traceId) {
        return claim(scope, key, tenantId, traceId);
    }

    /** Persist the first-accepted side-effect pointer (G3 submit-start {@code result_ref}). */
    default void remember(String scope, String key, String resultRef) {}

    default Optional<String> resultRef(String scope, String key) {
        return Optional.empty();
    }
}
