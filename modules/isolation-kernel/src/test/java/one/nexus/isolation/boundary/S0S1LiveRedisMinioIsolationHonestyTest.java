package one.nexus.isolation.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * S0+S1 leftover honesty: fixture tenant-key / bucket-prefix reject-before-sink
 * always-green; live Redis 3.2 / MinIO isolation is honest SKIP unless a live
 * walk was observed. Orthogonal to #174 G9 Redis-down and #169 G10 file-reconcile.
 * Does not claim product G9/G10 close or a product live drill close.
 */
class S0S1LiveRedisMinioIsolationHonestyTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("ops.summary"), sink);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void fixtureTenantKeyRejectsForeignKeyBeforeSink() {
        AccessContextHolder.set(tenant("alpha"));
        AtomicInteger writes = new AtomicInteger();
        CacheKeyGuard.put("drill", "p3", "row", "owned-by-a", (k, v) -> writes.incrementAndGet());
        assertEquals(1, writes.get());
        IsolationException ex = assertThrows(IsolationException.class, () ->
                CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:drill:beta:p3:row"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, ex.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        assertEquals(1, writes.get(), "P1.M02.014 must not invoke the cache sink");
        assertEquals("nexus:drill:alpha:p3:row",
                CacheKeyGuard.key("drill", AccessContextHolder.current(), "p3", "row"));
    }

    @Test
    void fixtureBucketPrefixRejectsForeignPrefixBeforeSink() {
        AccessContextHolder.set(tenant("alpha"));
        AtomicInteger puts = new AtomicInteger();
        FileObjectKeyGuard.putObject("ledger/doc.txt", new byte[] {1}, key -> puts.incrementAndGet());
        assertEquals(1, puts.get());
        IsolationException ex = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), "tenants/beta/ledger/doc.txt"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, ex.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        assertEquals(1, puts.get(), "P1.M02.015 must not invoke the storage sink");
        assertEquals("tenants/alpha/ledger/doc.txt",
                FileObjectKeyGuard.objectKey(AccessContextHolder.current(), "ledger/doc.txt"));
    }

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        S0S1LiveRedisMinioIsolationHonesty.Inventory inventory =
                S0S1LiveRedisMinioIsolationHonesty.probeLive();
        assertEquals("SKIP", inventory.liveRedisIsolation());
        assertEquals("SKIP", inventory.liveMinioIsolation());
        assertFalse(inventory.redisSkipReason().isBlank());
        assertFalse(inventory.minioSkipReason().isBlank());

        Map<String, String> report = S0S1LiveRedisMinioIsolationHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        S0S1LiveRedisMinioIsolationHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("NOT_CLAIMED", report.get("g10_close"));
        assertEquals("NOT_CLAIMED", report.get("live_drill_close"));
        assertEquals("false", report.get("g9_redis_down_174_redone"));
        assertEquals("false", report.get("g10_file_reconcile_169_redone"));
        assertEquals("false", report.get("g9_83_100_121_148_161_redone"));
        assertEquals("false", report.get("p0_05_06_redone"));
        assertEquals("PASS", report.get("fixture_tenant_key_guard"));
        assertEquals("PASS", report.get("fixture_bucket_prefix_guard"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        S0S1LiveRedisMinioIsolationHonesty.verify(
                S0S1LiveRedisMinioIsolationHonesty.parseReport(fixtureFile("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveRedisPass() {
        Map<String, String> fake = new HashMap<>(S0S1LiveRedisMinioIsolationHonesty.parseReport(
                fixtureFile("fake-live-redis-isolation-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_redis_isolation"));
    }

    @Test
    void honestyGateRejectsFakeLiveMinioPass() {
        Map<String, String> fake = new HashMap<>(S0S1LiveRedisMinioIsolationHonesty.parseReport(
                fixtureFile("fake-live-minio-isolation-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_minio_isolation"));
    }

    @Test
    void honestyGateRejectsG9AndG10CloseClaims() {
        Map<String, String> fake = new HashMap<>(S0S1LiveRedisMinioIsolationHonesty.parseReport(
                fixtureFile("fake-g9-close.env")));
        IllegalArgumentException g9 = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(g9.getMessage().contains("g9_close"));

        Map<String, String> g10 = new HashMap<>(S0S1LiveRedisMinioIsolationHonesty.parseReport(
                fixtureFile("fake-g10-close.env")));
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(g10));
        assertTrue(close.getMessage().contains("g10_close"));
    }

    @Test
    void docsReadmeKeepsSiblingLeftoverRows() throws IOException {
        String text = Files.readString(repoFile("docs/README.md"), StandardCharsets.UTF_8);
        for (String row : new String[] {
                "[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)",
                "[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)",
                "[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)",
                "[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)",
                "[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)",
                "[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)",
                "[g5-browser-failed-publish](./g5-browser-failed-publish.md)",
                "[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)",
                "[g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md)",
                "[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)",
                "[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)",
                "[g17-separation-honesty](./g17-separation-honesty.md)"
        }) {
            assertTrue(text.contains(row), "missing leftover row " + row);
        }
        assertFalse(text.contains("产品 live Redis/MinIO drill 已关闭"));
        assertFalse(text.contains("产品 G18 重开"));

        String claims = Files.readString(repoFile("docs/planning/团队领取任务清单_V1.1.md"),
                StandardCharsets.UTF_8);
        assertTrue(claims.contains("s0s1-live-redis-minio-isolation-honesty.md"));
        assertTrue(claims.contains("g1-fieldpolicy-mask-second-auth-honesty.md"));
        assertTrue(claims.contains("g10-identity-drift-honesty.md"));
        assertTrue(claims.contains("g15-native-bridge-honesty.md"));
        assertTrue(claims.contains("不要重开 G18"));
        assertFalse(claims.contains("产品 live Redis/MinIO drill 已关闭"));
    }

    @Test
    void honestyGateRejectsProductLiveDrillCloseAndRedoFlags() {
        Map<String, String> fake = new HashMap<>(S0S1LiveRedisMinioIsolationHonesty.parseReport(
                fixtureFile("honest-skip.env")));
        fake.put("live_drill_close", "CLOSED");
        IllegalArgumentException drill = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(drill.getMessage().contains("live_drill_close"));

        fake.put("live_drill_close", "NOT_CLAIMED");
        fake.put("g9_redis_down_174_redone", "true");
        IllegalArgumentException g9 = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(g9.getMessage().contains("g9_redis_down_174_redone"));

        fake.put("g9_redis_down_174_redone", "false");
        fake.put("g10_file_reconcile_169_redone", "true");
        IllegalArgumentException g10 = assertThrows(IllegalArgumentException.class,
                () -> S0S1LiveRedisMinioIsolationHonesty.verify(fake));
        assertTrue(g10.getMessage().contains("g10_file_reconcile_169_redone"));
    }

    private TenantContext tenant(String tenantId) {
        return kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-s0s1-iso-" + tenantId);
    }

    private static Path fixtureFile(String name) {
        Path[] candidates = {
                Path.of("scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/" + name),
                Path.of("../scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/" + name),
                Path.of("../../scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/" + name)
        };
        for (Path candidate : candidates) {
            if (candidate.toFile().isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("missing fixture " + name);
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        Path nested2 = Path.of("../..", relative);
        if (Files.isRegularFile(nested2)) {
            return nested2;
        }
        return Path.of(relative);
    }
}
