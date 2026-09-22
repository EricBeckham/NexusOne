package one.nexus.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import one.nexus.common.cache.LockHandle;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Process-local CachePort used when Redis 3.2 is not reachable. Still goes through
 * {@link CacheKeyGuard} so cross-tenant keys never hit the map.
 */
public final class InMemoryCacheAdapter implements CachePorts {
    private final String env;
    private final Clock clock;
    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

    public InMemoryCacheAdapter(String env, Clock clock) {
        this.env = Objects.requireNonNull(env, "env");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String backend() {
        return "memory";
    }

    @Override
    public Optional<String> get(String module, String key) {
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        return Optional.ofNullable(live(cacheKey)).map(Entry::value);
    }

    @Override
    public void put(String module, String key, String value, Duration ttl) {
        Objects.requireNonNull(value, "value");
        Duration safeTtl = requireTtl(ttl);
        CacheKeyGuard.put(env, module, key, value, (cacheKey, v) ->
                store.put(cacheKey, new Entry(v, Instant.now(clock).plus(safeTtl))));
    }

    @Override
    public boolean delete(String module, String key) {
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        return store.remove(cacheKey) != null;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String module, String key, Duration ttl) {
        Duration safeTtl = requireTtl(ttl);
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        String token = UUID.randomUUID().toString();
        Entry created = store.putIfAbsent(cacheKey, new Entry(token, Instant.now(clock).plus(safeTtl)));
        if (created != null && live(cacheKey) != null) {
            return Optional.empty();
        }
        if (created != null) {
            store.remove(cacheKey, created);
            if (store.putIfAbsent(cacheKey, new Entry(token, Instant.now(clock).plus(safeTtl))) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(new LockHandle(cacheKey, token));
    }

    @Override
    public boolean release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CacheKeyGuard.assertMatches(AccessContextHolder.current(), handle.cacheKey());
        Entry current = store.get(handle.cacheKey());
        if (current == null || !handle.token().equals(current.value)) {
            return false;
        }
        return store.remove(handle.cacheKey(), current);
    }

    @Override
    public long increment(String module, String key, Duration ttl) {
        Duration safeTtl = requireTtl(ttl);
        String cacheKey = GuardedCacheKeys.require(env, module, key);
        return store.compute(cacheKey, (k, existing) -> {
            long next = 1;
            if (existing != null && !existing.expired(clock)) {
                next = parseLong(existing.value) + 1;
            }
            return new Entry(Long.toString(next), Instant.now(clock).plus(safeTtl));
        }).valueAsLong();
    }

    private Entry live(String cacheKey) {
        Entry entry = store.get(cacheKey);
        if (entry == null || entry.expired(clock)) {
            if (entry != null) {
                store.remove(cacheKey, entry);
            }
            return null;
        }
        return entry;
    }

    private static Duration requireTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return ttl;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record Entry(String value, Instant expiresAt) {
        boolean expired(Clock clock) {
            return !Instant.now(clock).isBefore(expiresAt);
        }

        long valueAsLong() {
            return parseLong(value);
        }
    }
}
