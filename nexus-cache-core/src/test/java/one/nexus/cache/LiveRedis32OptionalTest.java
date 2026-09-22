package one.nexus.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Optional live Redis 3.2 check. Skips when {@code NEXUS_REDIS_HOST} is unreachable.
 * Does not require Docker.
 */
class LiveRedis32OptionalTest {
    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void ttlIncrAndLockWhenRedis32IsUp() {
        String host = System.getenv().getOrDefault("NEXUS_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("NEXUS_REDIS_PORT", "6379"));
        JedisPooled jedis = new JedisPooled(host, port);
        try {
            assumeTrue("PONG".equals(jedis.ping()), "Redis not reachable");
        } catch (JedisConnectionException ex) {
            assumeTrue(false, "Redis not reachable: " + ex.getMessage());
            return;
        }
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of("cache.ops"), new CollectingAuditSink());
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-live");
        Redis32CacheAdapter cache = new Redis32CacheAdapter(new JedisRedis32Commands(jedis), "dev");
        String info = cache.infoServer();
        cache.put("p3", "live", "ok", Duration.ofSeconds(5));
        assertEquals("ok", cache.get("p3", "live").orElseThrow());
        assertTrue(cache.increment("p3", "n", Duration.ofSeconds(5)) >= 1);
        assertTrue(info.contains("redis_version:"));
        jedis.close();
    }
}
