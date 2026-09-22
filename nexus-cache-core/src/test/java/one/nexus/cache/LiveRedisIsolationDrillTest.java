package one.nexus.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Optional live Redis isolation drill. Skips when {@code NEXUS_REDIS_HOST}
 * is unreachable. Proves {@code P1.M02.014} reject-before-execute with a
 * guard record. Does not require Docker.
 */
class LiveRedisIsolationDrillTest {
    private JedisPooled jedis;

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void foreignCacheKeyIsRejectedBeforeLiveRedisAndRecordsGuard() {
        String host = System.getenv().getOrDefault("NEXUS_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("NEXUS_REDIS_PORT", "6379"));
        try {
            jedis = new JedisPooled(host, port);
            assumeTrue("PONG".equals(jedis.ping()), "Live Redis not reachable at " + host + ":" + port);
        } catch (JedisConnectionException | IllegalArgumentException ex) {
            assumeTrue(false, "Live Redis not reachable: " + ex.getMessage());
            return;
        }

        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("cache.ops"), sink);
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-live-redis-a");

        CountingRedis32Commands redis = new CountingRedis32Commands(new JedisRedis32Commands(jedis));
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        String info = cache.infoServer();
        assertTrue(info.contains("redis_version:"), info);
        System.out.println("LIVE_REDIS_ISOLATION_DRILL version=" + redisVersion(info));

        String ownedKey = "g1-live-" + System.nanoTime();
        cache.put("p3", ownedKey, "owned-by-a", Duration.ofSeconds(30));
        String alphaKey = CacheKeyGuard.key("dev", AccessContextHolder.current(), "p3", ownedKey);
        assertEquals("owned-by-a", cache.get("p3", ownedKey).orElseThrow());
        assertEquals("owned-by-a", jedis.get(alphaKey));
        int afterWrite = redis.commandCount();

        IsolationException guard = assertThrows(IsolationException.class, () ->
                CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:" + ownedKey));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, guard.error());
        assertEquals("P1.M02.014", guard.code());

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, "beta", "user-b", "mem-beta", "trace-live-redis-b");
        IsolationException release = assertThrows(IsolationException.class, () ->
                cache.release(new LockHandle(alphaKey, "stolen-token")));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, release.error());

        assertEquals(afterWrite, redis.commandCount(), "reject must not emit a Redis command");
        assertFalse(Boolean.TRUE.equals(jedis.exists("nexus:dev:beta:p3:" + ownedKey)));
        assertEquals("owned-by-a", jedis.get(alphaKey));
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        assertTrue(sink.rejectCount() >= 2);
        jedis.del(alphaKey);
    }

    private static String redisVersion(String info) {
        for (String line : info.split("\\r?\\n")) {
            if (line.startsWith("redis_version:")) {
                return line.substring("redis_version:".length()).trim();
            }
        }
        return "unknown";
    }
}
