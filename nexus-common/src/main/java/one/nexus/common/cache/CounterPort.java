package one.nexus.common.cache;

import java.time.Duration;

/**
 * Atomic counter. Redis 3.2 implementation uses {@code INCR} plus Lua {@code EXPIRE}
 * on first increment. Not a source of business truth.
 */
public interface CounterPort {
    long increment(String module, String key, Duration ttl);
}
