package one.nexus.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
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
 * Optional live Redis isolation drill. Prefers dedicated {@code NEXUS_REDIS_PORT}
 * (ops default 16379). Skips when that host/port is unreachable. Does not bind
 * product {@code :6379} unless the operator pointed the env there.
 */
class LiveRedisIsolationOptionalTest {
    private static final String TENANT_A = "alpha";
    private static final String TENANT_B = "beta";
    private static final String ENV = "drill";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void foreignCacheKeyRejectedBeforeLiveRedisCommand() {
        String host = System.getenv().getOrDefault("NEXUS_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("NEXUS_REDIS_PORT", "16379"));
        JedisPooled jedis = new JedisPooled(host, port);
        try {
            assumeTrue("PONG".equals(jedis.ping()), "live Redis not reachable at " + host + ":" + port);
        } catch (JedisConnectionException ex) {
            assumeTrue(false, "live Redis not reachable at " + host + ":" + port + ": " + ex.getMessage());
            return;
        }

        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("cache.ops"), sink);
        CountingRedis32Commands counted = new CountingRedis32Commands(new JedisRedis32Commands(jedis));
        Redis32CacheAdapter cache = new Redis32CacheAdapter(counted, ENV);
        String run = "iso-" + UUID.randomUUID();
        String ownedKey = "nexus:" + ENV + ":" + TENANT_A + ":p3:" + run;
        String foreignKey = "nexus:" + ENV + ":" + TENANT_B + ":p3:" + run;
        try {
            IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-live-redis-a");
            cache.put("p3", run, "owned-by-a", Duration.ofSeconds(30));
            jedis.setex(foreignKey, 30, "owned-by-b");
            int afterSeed = counted.commandCount();

            IsolationException reject = assertThrows(IsolationException.class, () ->
                    CacheKeyGuard.assertMatches(AccessContextHolder.current(), foreignKey));
            assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, reject.error());
            assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
            assertEquals(afterSeed, counted.commandCount(), "P1.M02.014 must not send a Redis command");

            assertEquals(ownedKey, CacheKeyGuard.key(ENV, AccessContextHolder.current(), "p3", run));
            assertEquals("owned-by-a", cache.get("p3", run).orElseThrow());
            assertEquals("owned-by-b", jedis.get(foreignKey));
            assertEquals("owned-by-a", jedis.get(ownedKey));
            assertTrue(counted.ops().stream().noneMatch(op -> op.contains("FLUSH")));
            System.out.println("LIVE redis isolation PASS host=" + host + " port=" + port
                    + " version=" + versionLine(cache.infoServer())
                    + " reject=P1.M02.014 commandsUnchanged=" + afterSeed);
        } finally {
            jedis.del(ownedKey);
            jedis.del(foreignKey);
            jedis.close();
        }
    }

    private static String versionLine(String info) {
        return info.lines()
                .filter(line -> line.startsWith("redis_version:"))
                .findFirst()
                .orElse("redis_version:unknown");
    }
}
