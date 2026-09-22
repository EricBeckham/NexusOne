package one.nexus.cache;

import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import one.nexus.common.cache.CachePort;
import one.nexus.common.cache.CounterPort;
import one.nexus.common.cache.DistributedLockPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import redis.clients.jedis.JedisPooled;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    IsolationKernel isolationKernel(ObjectProvider<IsolationAuditSink> sinks) {
        return IsolationKernel.create(
                new TableRegistry(),
                Set.of("files.reconcile", "notify.ops", "cache.ops"),
                sinks.getIfAvailable(CollectingAuditSink::new));
    }

    @Bean
    @ConditionalOnMissingBean
    Clock cacheClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CachePorts.class)
    CachePorts cachePorts(CacheProperties properties, Clock cacheClock) {
        String mode = properties.getMode() == null ? "auto" : properties.getMode();
        if ("memory".equalsIgnoreCase(mode)) {
            return new InMemoryCacheAdapter(properties.getEnv(), cacheClock);
        }
        if ("redis".equalsIgnoreCase(mode)) {
            return redis(properties);
        }
        try {
            Redis32CacheAdapter adapter = redis(properties);
            adapter.ping();
            log.info("CachePort backend=redis32 {}", Redis32CommandMatrix.summary());
            return adapter;
        } catch (RuntimeException ex) {
            log.warn("Redis 3.2 unavailable at {}:{}, using in-memory CachePort ({})",
                    properties.getHost(), properties.getPort(), ex.getMessage());
            return new InMemoryCacheAdapter(properties.getEnv(), cacheClock);
        }
    }

    @Bean
    CachePort cachePort(CachePorts ports) {
        return ports;
    }

    @Bean
    DistributedLockPort distributedLockPort(CachePorts ports) {
        return ports;
    }

    @Bean
    CounterPort counterPort(CachePorts ports) {
        return ports;
    }

    private static Redis32CacheAdapter redis(CacheProperties properties) {
        JedisPooled jedis = new JedisPooled(properties.getHost(), properties.getPort());
        return new Redis32CacheAdapter(new JedisRedis32Commands(jedis), properties.getEnv());
    }
}
