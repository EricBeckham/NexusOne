package one.nexus.cache;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.cache.LockHandle;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Redis 3.2 CachePort. Only emits commands listed in {@link Redis32CommandMatrix#SUPPORTED}.
 */
public final class Redis32CacheAdapter implements CachePorts {
    static final String UNLOCK_LUA =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";
    static final String INCR_EXPIRE_LUA =
            "local v = redis.call('INCR', KEYS[1]) if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return v";

    private final Redis32Commands redis;
    private final String env;

    public Redis32CacheAdapter(Redis32Commands redis, String env) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.env = Objects.requireNonNull(env, "env");
    }

    @Override
    public String backend() {
        return "redis32";
    }

    public String ping() {
        return redis.ping();
    }

    public String infoServer() {
        return redis.infoServer();
    }

    @Override
    public Optional<String> get(String module, String key) {
        return Optional.ofNullable(redis.get(GuardedCacheKeys.require(env, module, key)));
    }

    @Override
    public void put(String module, String key, String value, Duration ttl) {
        Objects.requireNonNull(value, "value");
        long seconds = requireSeconds(ttl);
        CacheKeyGuard.put(env, module, key, value, (cacheKey, v) -> redis.setEx(cacheKey, seconds, v));
    }

    @Override
    public boolean delete(String module, String key) {
        return redis.del(GuardedCacheKeys.require(env, module, key)) > 0;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String module, String key, Duration ttl) {
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        String token = UUID.randomUUID().toString();
        String result = redis.setNxPx(cacheKey, token, requireMillis(ttl));
        if (!"OK".equals(result)) {
            return Optional.empty();
        }
        return Optional.of(new LockHandle(cacheKey, token));
    }

    @Override
    public boolean release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CacheKeyGuard.assertMatches(AccessContextHolder.current(), handle.cacheKey());
        Object result = redis.eval(UNLOCK_LUA, List.of(handle.cacheKey()), List.of(handle.token()));
        return asLong(result) == 1L;
    }

    @Override
    public long increment(String module, String key, Duration ttl) {
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        Object result = redis.eval(INCR_EXPIRE_LUA, List.of(cacheKey), List.of(Long.toString(requireSeconds(ttl))));
        return asLong(result);
    }

    private static long requireSeconds(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        long seconds = Math.max(1, ttl.toSeconds());
        return seconds;
    }

    private static long requireMillis(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return Math.max(1, ttl.toMillis());
    }

    private static long asLong(Object result) {
        if (result instanceof Number number) {
            return number.longValue();
        }
        throw new NexusException(ErrorCode.CACHE_UNAVAILABLE, 503, "unexpected Redis EVAL result");
    }
}
