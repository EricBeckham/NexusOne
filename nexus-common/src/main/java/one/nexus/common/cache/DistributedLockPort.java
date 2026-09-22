package one.nexus.common.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * UX-level lock. Redis 3.2 implementation uses {@code SET NX PX} and a compare-and-DEL Lua
 * script. Must not store payment / outbox / install idempotency keys.
 */
public interface DistributedLockPort {
    Optional<LockHandle> tryAcquire(String module, String key, Duration ttl);

    boolean release(LockHandle handle);
}
