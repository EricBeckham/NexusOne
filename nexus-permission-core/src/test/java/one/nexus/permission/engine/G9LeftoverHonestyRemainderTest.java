package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import one.nexus.permission.redis.G9LeftoverHonestyRemainder;
import one.nexus.permission.redis.G9LeftoverHonestyRemainder.LiveInventory;
import one.nexus.permission.redis.G9LeftoverHonestyRemainder.ParentHarness;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover remainder beyond #237 / #229 / #209 / #197: parent leftover
 * harness stays always-green; live Sentinel / multi-JVM / Redis-down /
 * invalidate is honest SKIP when unobserved. Does not redo those leftovers.
 * Does not claim product G9 close. G18 stays closed.
 */
class G9LeftoverHonestyRemainderTest {

    @Test
    void parentHarnessStillPassesWithoutRedoing237Or229Or209Or197() {
        ParentHarness parent = G9LeftoverHonestyRemainder.proveParentHarness();
        assertEquals("PASS", parent.leftover237());
        assertEquals("PASS", parent.leftover229());
        assertEquals("PASS", parent.leftover209());
        assertEquals("PASS", parent.leftover197());
        assertEquals("PASS", parent.leftover237Cells());
        assertEquals("PASS", parent.leftover229Cells());
        assertEquals("PASS", parent.leftover209Cells());
        assertEquals("PASS", parent.leftover197Cells());
    }

    @Test
    void liveWalksAreHonestSkipWhenUnobserved() {
        LiveInventory live = G9LeftoverHonestyRemainder.probeLive();
        assertEquals("SKIP", live.liveRedisDown());
        assertEquals("SKIP", live.liveInvalidate());
        assertEquals("SKIP", live.sentinelFailover());
        assertEquals("SKIP", live.clusterFailover());
        assertEquals("SKIP", live.replicaPromotion());
        assertEquals("SKIP", live.liveMultiJvm());
        assertFalse(live.liveRedisDownSkipReason().isBlank());
        assertFalse(live.liveInvalidateSkipReason().isBlank());
        assertFalse(live.sentinelSkipReason().isBlank());
        assertFalse(live.clusterSkipReason().isBlank());
        assertFalse(live.replicaSkipReason().isBlank());
        assertFalse(live.multiJvmSkipReason().isBlank());
        assertTrue(live.multiJvmSkipReason().contains("surefire")
                || live.multiJvmSkipReason().contains("one application JVM"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        ParentHarness parent = G9LeftoverHonestyRemainder.proveParentHarness();
        LiveInventory live = G9LeftoverHonestyRemainder.probeLive();
        Map<String, String> report = G9LeftoverHonestyRemainder.honestSkipTemplate(parent, live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9LeftoverHonestyRemainder.verify(report);
        assertEquals("PASS", report.get("leftover_237_harness"));
        assertEquals("PASS", report.get("leftover_229_harness"));
        assertEquals("PASS", report.get("leftover_209_harness"));
        assertEquals("PASS", report.get("leftover_197_harness"));
        assertEquals("SKIP", report.get("live_redis_down"));
        assertEquals("SKIP", report.get("live_invalidate"));
        assertEquals("SKIP", report.get("sentinel_failover"));
        assertEquals("SKIP", report.get("cluster_failover"));
        assertEquals("SKIP", report.get("replica_promotion"));
        assertEquals("SKIP", report.get("live_multi_jvm"));
        assertEquals(G9LeftoverHonestyRemainder.G9_CLOSE, report.get("g9_close"));
        assertEquals(G9LeftoverHonestyRemainder.SENTINEL_CLUSTER_CLOSE, report.get("sentinel_cluster_close"));
        assertEquals("false", report.get("redo_237"));
        assertEquals("false", report.get("redo_229"));
        assertEquals("false", report.get("redo_209"));
        assertEquals("false", report.get("redo_197"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-live-multi-jvm-replica-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-sentinel-cluster-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-remainder"));
        assertTrue(report.get("leftover_rows").contains("s0-secret-rotate-getenv-leftover-honesty"));
        assertTrue(report.get("leftover_rows").contains("g5-publishhook-ddl-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-mfa-recovery-vue-otp-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g10-file-object-reconcile-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g4-hot-purge-staging-pkce-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-separation-leftover-honesty-remainder"));
        assertTrue(report.get("leftover_rows").contains("g15-h5-pkce-miniprogram-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-h5-every-page-form-write-report-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("linux-arm-profile-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-leftover-honesty-remainder"));
        G9LeftoverHonestyRemainder.verify(
                G9LeftoverHonestyRemainder.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePassAndG9CloseAndRedo() {
        IllegalArgumentException down = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-live-redis-down-pass.env"))));
        assertTrue(down.getMessage().contains("live_redis_down"));

        IllegalArgumentException invalidate = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-live-invalidate-pass.env"))));
        assertTrue(invalidate.getMessage().contains("live_invalidate"));

        IllegalArgumentException sentinel = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-sentinel-pass.env"))));
        assertTrue(sentinel.getMessage().contains("sentinel_failover"));

        IllegalArgumentException jvm = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-multi-jvm-pass.env"))));
        assertTrue(jvm.getMessage().contains("live_multi_jvm"));

        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-g9-close.env"))));
        assertTrue(close.getMessage().contains("g9_close")
                || close.getMessage().contains("sentinel_cluster_close"));

        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G9LeftoverHonestyRemainder.verify(
                        G9LeftoverHonestyRemainder.parseReport(fixture("fake-redone.env"))));
        assertTrue(redo.getMessage().contains("redo_237")
                || redo.getMessage().contains("redo_229")
                || redo.getMessage().contains("redo_209")
                || redo.getMessage().contains("redo_197"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndRefuseG9Close() throws IOException {
        String leftover = readExisting(repoFile("docs/g9-leftover-honesty-remainder.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#237"));
        assertTrue(leftover.contains("#229"));
        assertTrue(leftover.contains("#209"));
        assertTrue(leftover.contains("#197"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(leftover.contains("g9-live-multi-jvm-replica-remainder-honesty"));
        assertTrue(leftover.contains("g9-sentinel-cluster-remainder-honesty"));
        assertTrue(leftover.contains("s0-secret-rotate-getenv-leftover-honesty"));
        assertTrue(leftover.contains("g5-publishhook-ddl-remainder-honesty"));
        assertTrue(leftover.contains("g17-mfa-recovery-vue-otp-remainder-honesty"));
        assertTrue(leftover.contains("g10-file-object-reconcile-remainder-honesty"));
        assertTrue(leftover.contains("g4-hot-purge-staging-pkce-remainder-honesty"));
        assertTrue(leftover.contains("g17-separation-leftover-honesty-remainder"));
        assertTrue(leftover.contains("g15-h5-pkce-miniprogram-remainder-honesty"));
        assertTrue(leftover.contains("g2-h5-every-page-form-write-report-remainder-honesty"));
        assertTrue(leftover.contains("linux-arm-profile-remainder-honesty"));
        assertTrue(leftover.contains("does **not** redo #237") || leftover.contains("Does **not** redo #237")
                || leftover.contains("不重做 #237"));
        assertFalse(leftover.contains("产品 G9 已关闭"));
        assertFalse(lower.contains("product g9 is closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : G9LeftoverHonestyRemainder.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g9 closed"));
        assertFalse(readme.contains("产品 G9 已关闭"));
    }

    @Test
    void claimListKeepsG9LeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g9-leftover-honesty-remainder.md"));
        assertTrue(text.contains("g9-redis-down-invalidate-remainder-honesty.md"));
        assertTrue(text.contains("g9-live-multi-jvm-replica-remainder-honesty.md"));
        assertTrue(text.contains("g9-sentinel-cluster-remainder-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-getenv-leftover-honesty.md"));
        assertTrue(text.contains("g5-publishhook-ddl-remainder-honesty.md"));
        assertTrue(text.contains("g17-mfa-recovery-vue-otp-remainder-honesty.md"));
        assertTrue(text.contains("g10-file-object-reconcile-remainder-honesty.md"));
        assertTrue(text.contains("g4-hot-purge-staging-pkce-remainder-honesty.md"));
        assertTrue(text.contains("g17-separation-leftover-honesty-remainder.md"));
        assertTrue(text.contains("g15-h5-pkce-miniprogram-remainder-honesty.md"));
        assertTrue(text.contains("g2-h5-every-page-form-write-report-remainder-honesty.md"));
        assertTrue(text.contains("linux-arm-profile-remainder-honesty.md"));
        assertTrue(text.contains("#237"));
        assertTrue(text.contains("#229"));
        assertTrue(text.contains("#209"));
        assertTrue(text.contains("#197"));
        assertTrue(text.contains("不宣称产品 G9 关闭") || text.contains("不宣称 Sentinel/Cluster"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G9 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-leftover-honesty-remainder/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-leftover-honesty-remainder/fixtures/" + name);
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private static String readExisting(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        return Files.readString(file);
    }
}
