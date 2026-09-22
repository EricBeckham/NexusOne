package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.HashChainVerification;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.cache.IsolationSupport;
import one.nexus.cache.JedisRedis32Commands;
import one.nexus.cache.Redis32CacheAdapter;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.cache.LockHandle;
import one.nexus.common.file.FileRef;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.file.FileService;
import one.nexus.file.ObjectStoragePort;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.file.infra.MinioObjectStorage;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Optional live Redis / MinIO G1 drills on the product {@link AuditPortIsolationSink}.
 * Each test skips when that live service is absent. Guard records use
 * {@link AuditPort#appendNewTransaction}; same-TX {@link AuditPort#append} is
 * not called and is not changed.
 */
class LiveG1CacheFileDrillTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void liveRedisRejectRecordsGuardViaNewTransactionAndNeverHitsBackend() {
        String host = System.getenv().getOrDefault("NEXUS_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("NEXUS_REDIS_PORT", "6379"));
        JedisPooled jedis;
        try {
            jedis = new JedisPooled(host, port);
            assumeTrue("PONG".equals(jedis.ping()), "Live Redis not reachable at " + host + ":" + port);
        } catch (JedisConnectionException | IllegalArgumentException ex) {
            assumeTrue(false, "Live Redis not reachable: " + ex.getMessage());
            return;
        }

        RecordingAuditPort audit = RecordingAuditPort.ephemeral();
        CollectingAuditSink collecting = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(),
                Set.of("cache.ops"),
                IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(audit, event -> {})));
        IsolationSupport.bindTenant(kernel, "alpha", "user-a", "mem-alpha", "trace-g1-live-redis");

        CommandCountingRedis redis = new CommandCountingRedis(new JedisRedis32Commands(jedis));
        Redis32CacheAdapter cache = new Redis32CacheAdapter(redis, "dev");
        String ownedKey = "g1-live-boot-" + System.nanoTime();
        cache.put("p3", ownedKey, "owned-by-a", Duration.ofSeconds(30));
        String alphaKey = CacheKeyGuard.key("dev", AccessContextHolder.current(), "p3", ownedKey);
        int afterWrite = redis.commands.get();

        IsolationException guard = assertThrows(IsolationException.class, () ->
                CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:" + ownedKey));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, guard.error());

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, "beta", "user-b", "mem-beta", "trace-g1-live-redis-b");
        IsolationException release = assertThrows(IsolationException.class, () ->
                cache.release(new LockHandle(alphaKey, "stolen-token")));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, release.error());

        assertEquals(afterWrite, redis.commands.get());
        assertFalse(Boolean.TRUE.equals(jedis.exists("nexus:dev:beta:p3:" + ownedKey)));
        assertEquals("owned-by-a", jedis.get(alphaKey));
        jedis.del(alphaKey);
        jedis.close();

        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        assertEquals(0, audit.appendCalls.get(), "same-TX append must stay unused on reject");
        assertTrue(audit.newTxCalls.get() >= 2);
        assertTrue(audit.findByTraceId("trace-g1-live-redis").stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.014".equals(event.event().resourceId())));
        assertTrue(audit.verifyChain().intact());
        System.out.println("LIVE_G1_REDIS_DRILL guard=P1.M02.014 newTx=" + audit.newTxCalls.get()
                + " append=" + audit.appendCalls.get());
    }

    @Test
    void liveMinioRejectRecordsGuardViaNewTransactionAndNeverHitsBackend() {
        String endpoint = env("S3_ENDPOINT");
        String accessKey = env("S3_ACCESS_KEY");
        String secretKey = env("S3_SECRET_KEY");
        String bucket = System.getenv().getOrDefault("S3_TEST_BUCKET", "nexus-g1-live");
        assumeTrue(notBlank(endpoint) && notBlank(accessKey) && notBlank(secretKey),
                "Live MinIO skipped: set S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY");

        MinioClient probe;
        try {
            probe = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            assumeTrue(probe.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()),
                    "Live MinIO skipped: bucket " + bucket + " is not present");
        } catch (Exception ex) {
            assumeTrue(false, "Live MinIO not reachable: " + ex.getMessage());
            return;
        }

        RecordingAuditPort port = RecordingAuditPort.ephemeral();
        CollectingAuditSink collecting = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(),
                Set.of("files.reconcile"),
                IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-g1-live-minio");

        CommandCountingStorage storage = new CommandCountingStorage(
                new MinioObjectStorage(endpoint, accessKey, secretKey));
        FileService service = new FileService(
                storage,
                new InMemoryFileObjectRepository(),
                (subject, resource) -> true,
                Clock.systemUTC(),
                bucket,
                1024,
                Duration.ofSeconds(60),
                Set.of("text/plain"));
        FileRef ref = service.upload("ledger", "g1-live.txt", "text/plain", "hello-live".getBytes(), "record-live");
        int puts = storage.puts.get();
        int signs = storage.signs.get();
        int gets = storage.gets.get();

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-g1-live-minio-b");
        IsolationException sign = assertThrows(IsolationException.class, () ->
                service.signDownload(ref.fileId(), "user-a"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, sign.error());
        IsolationException download = assertThrows(IsolationException.class, () ->
                service.download(ref.fileId()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, download.error());
        IsolationException prefix = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), ref.objectKey()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, prefix.error());

        assertEquals(puts, storage.puts.get());
        assertEquals(signs, storage.signs.get());
        assertEquals(gets, storage.gets.get());
        try {
            probe.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(ref.objectKey()).build());
        } catch (Exception ignored) {
            // best-effort cleanup
        }

        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        assertEquals(0, port.appendCalls.get(), "same-TX append must stay unused on reject");
        assertTrue(port.newTxCalls.get() >= 3);
        assertTrue(port.findByTraceId("trace-g1-live-minio-b").stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.015".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
        System.out.println("LIVE_G1_MINIO_DRILL guard=P1.M02.015 newTx=" + port.newTxCalls.get()
                + " append=" + port.appendCalls.get() + " object=" + ref.objectKey());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static final class CommandCountingRedis implements one.nexus.cache.Redis32Commands {
        private final one.nexus.cache.Redis32Commands delegate;
        private final AtomicInteger commands = new AtomicInteger();

        private CommandCountingRedis(one.nexus.cache.Redis32Commands delegate) {
            this.delegate = delegate;
        }

        @Override
        public String ping() {
            commands.incrementAndGet();
            return delegate.ping();
        }

        @Override
        public String get(String key) {
            commands.incrementAndGet();
            return delegate.get(key);
        }

        @Override
        public String setEx(String key, long seconds, String value) {
            commands.incrementAndGet();
            return delegate.setEx(key, seconds, value);
        }

        @Override
        public String setNxPx(String key, String value, long ttlMs) {
            commands.incrementAndGet();
            return delegate.setNxPx(key, value, ttlMs);
        }

        @Override
        public long del(String key) {
            commands.incrementAndGet();
            return delegate.del(key);
        }

        @Override
        public long incr(String key) {
            commands.incrementAndGet();
            return delegate.incr(key);
        }

        @Override
        public Object eval(String script, java.util.List<String> keys, java.util.List<String> args) {
            commands.incrementAndGet();
            return delegate.eval(script, keys, args);
        }

        @Override
        public String infoServer() {
            commands.incrementAndGet();
            return delegate.infoServer();
        }
    }

    private static final class CommandCountingStorage implements ObjectStoragePort {
        private final ObjectStoragePort delegate;
        private final AtomicInteger puts = new AtomicInteger();
        private final AtomicInteger gets = new AtomicInteger();
        private final AtomicInteger signs = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();

        private CommandCountingStorage(ObjectStoragePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void put(String bucket, String objectKey, byte[] body, String contentType, long partSize) {
            puts.incrementAndGet();
            delegate.put(bucket, objectKey, body, contentType, partSize);
        }

        @Override
        public byte[] get(String bucket, String objectKey) {
            gets.incrementAndGet();
            return delegate.get(bucket, objectKey);
        }

        @Override
        public String presignGet(String bucket, String objectKey, Duration ttl) {
            signs.incrementAndGet();
            return delegate.presignGet(bucket, objectKey, ttl);
        }

        @Override
        public void delete(String bucket, String objectKey) {
            deletes.incrementAndGet();
            delegate.delete(bucket, objectKey);
        }
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final InMemoryHashChainedAuditPort inner;
        private final AtomicInteger appendCalls = new AtomicInteger();
        private final AtomicInteger newTxCalls = new AtomicInteger();

        private RecordingAuditPort(InMemoryHashChainedAuditPort inner) {
            this.inner = inner;
        }

        static RecordingAuditPort ephemeral() {
            return new RecordingAuditPort(new InMemoryHashChainedAuditPort(
                    JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL)));
        }

        @Override
        public ChainedAuditEvent append(AuditEvent event) {
            appendCalls.incrementAndGet();
            return inner.append(event);
        }

        @Override
        public ChainedAuditEvent appendNewTransaction(AuditEvent event) {
            newTxCalls.incrementAndGet();
            return inner.appendNewTransaction(event);
        }

        @Override
        public List<ChainedAuditEvent> findByTraceId(String traceId) {
            return inner.findByTraceId(traceId);
        }

        @Override
        public List<ChainedAuditEvent> findByCorrelationId(String correlationId) {
            return inner.findByCorrelationId(correlationId);
        }

        @Override
        public Optional<ChainedAuditEvent> findByEventId(UUID eventId) {
            return inner.findByEventId(eventId);
        }

        @Override
        public List<ChainedAuditEvent> chain() {
            return inner.chain();
        }

        @Override
        public HashChainVerification verifyChain() {
            return inner.verifyChain();
        }
    }
}
