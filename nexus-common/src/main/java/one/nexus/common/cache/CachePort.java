package one.nexus.common.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Tenant-scoped cache. Implementations must build keys through
 * {@code CacheKeyGuard} and must not talk to Redis before the guard accepts.
 */
public interface CachePort {
    Optional<String> get(String module, String key);

    void put(String module, String key, String value, Duration ttl);

    boolean delete(String module, String key);
}
