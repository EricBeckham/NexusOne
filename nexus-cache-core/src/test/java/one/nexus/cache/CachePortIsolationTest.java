package one.nexus.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import one.nexus.common.cache.LockHandle;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CachePortIsolationTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("cache.ops"), sink);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void putGetIncrLockUseTenantPrefixedKeys() {
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-a");
        InMemoryCacheAdapter cache = new InMemoryCacheAdapter("dev", Clock.systemUTC());
        cache.put("p3", "row", "v1", Duration.ofSeconds(30));
        assertEquals("v1", cache.get("p3", "row").orElseThrow());
        assertEquals("nexus:dev:alpha:p3:row", CacheKeyGuard.key("dev", AccessContextHolder.current(), "p3", "row"));
        assertEquals(1L, cache.increment("p3", "counter", Duration.ofSeconds(30)));
        assertEquals(2L, cache.increment("p3", "counter", Duration.ofSeconds(30)));
        LockHandle lock = cache.tryAcquire("lock", "submit", Duration.ofSeconds(5)).orElseThrow();
        assertTrue(cache.tryAcquire("lock", "submit", Duration.ofSeconds(5)).isEmpty());
        assertTrue(cache.release(lock));
        assertTrue(cache.tryAcquire("lock", "submit", Duration.ofSeconds(5)).isPresent());
    }

    @Test
    void foreignTenantKeyIsRejectedBeforeRedis() {
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-a");
        FakeRedis32Commands redis = new FakeRedis32Commands();
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        cache.put("p3", "row", "v1", Duration.ofSeconds(30));
        int afterWrite = redis.commandCount();
        IsolationException ex = assertThrows(IsolationException.class, () ->
                CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:row"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, ex.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        assertEquals(afterWrite, redis.commandCount());
        assertFalse(redis.contains("nexus:dev:beta:p3:row"));
    }

    @Test
    void redisAdapterUsesOnly32SafeCommands() {
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-a");
        FakeRedis32Commands redis = new FakeRedis32Commands();
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        cache.put("p3", "row", "v1", Duration.ofSeconds(30));
        assertEquals("v1", cache.get("p3", "row").orElseThrow());
        assertEquals(1L, cache.increment("p3", "n", Duration.ofSeconds(30)));
        LockHandle lock = cache.tryAcquire("lock", "k", Duration.ofSeconds(5)).orElseThrow();
        assertTrue(cache.release(lock));
        assertTrue(Redis32CommandMatrix.UNSUPPORTED.stream().anyMatch(item -> item.contains("GETEX")));
        assertTrue(Redis32CommandMatrix.SUPPORTED.contains("EVAL / EVALSHA (Lua 5.1)"));
    }

    @Test
    void missingContextIsRejectedBeforeSink() {
        FakeRedis32Commands redis = new FakeRedis32Commands();
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        IsolationException ex = assertThrows(IsolationException.class, () ->
                cache.put("p3", "row", "v", Duration.ofSeconds(1)));
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
        assertTrue(sink.rejected(IsolationErrorCode.MISSING_CONTEXT));
        assertEquals(0, redis.commandCount());
    }

    @Test
    void wrongTokenCannotUnlock() {
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-a");
        FakeRedis32Commands redis = new FakeRedis32Commands();
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        LockHandle lock = cache.tryAcquire("lock", "k", Duration.ofSeconds(5)).orElseThrow();
        assertFalse(cache.release(new LockHandle(lock.cacheKey(), "other-token")));
        assertTrue(redis.contains(lock.cacheKey()));
        assertTrue(cache.release(lock));
    }
}
