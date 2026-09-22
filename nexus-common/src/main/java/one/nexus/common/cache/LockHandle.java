package one.nexus.common.cache;

import java.util.Objects;

/**
 * Lease token for a distributed lock. Release is token-safe: a late holder must not
 * delete a newer owner's key.
 */
public record LockHandle(String cacheKey, String token) {
    public LockHandle {
        Objects.requireNonNull(cacheKey, "cacheKey");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
    }
}
