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
import one.nexus.permission.redis.G9SentinelClusterRemainderHonesty;
import one.nexus.permission.redis.G9SentinelClusterRemainderHonesty.InventoryHarness;
import one.nexus.permission.redis.G9SentinelClusterRemainderHonesty.LiveObservation;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #148 / #161: Sentinel / Cluster remainder honesty.
 * Standalone INFO / SENTINEL|CLUSTER inventory stub PASS; live Sentinel /
 * Cluster observation honest SKIP. Does not redo #83/#100/#121/#128/#148/#161/#174.
 * Does not claim product G9 close. G18 stays closed.
 */
class G9SentinelClusterRemainderHonestyTest {

    @Test
    void standaloneInventoryStubPassesWithoutSentinelOrCluster() {
        InventoryHarness harness = G9SentinelClusterRemainderHonesty.proveStandaloneInventoryStub();
        assertEquals("3.2.13", harness.redisVersion());
        assertEquals("standalone", harness.redisMode());
        assertEquals("master", harness.redisRole());
        assertEquals(0, harness.connectedSlaves());
        assertFalse(harness.sentinelCommandPresent());
        assertFalse(harness.clusterEnabled());
        assertEquals("PASS", harness.verdict());
    }

    @Test
    void liveProbeCannotClaimSentinelOrClusterFailover() {
        LiveObservation live = G9SentinelClusterRemainderHonesty.probeLive();
        assertEquals("SKIP", live.sentinelFailover());
        assertEquals("SKIP", live.clusterFailover());
        assertFalse(live.sentinelSkipReason().isBlank());
        assertFalse(live.clusterSkipReason().isBlank());
        assertFalse(live.sentinelFailoverObserved());
        assertFalse(live.clusterFailoverObserved());
        if (!live.redisPresent()) {
            assertFalse(live.sentinelPresent());
            assertFalse(live.clusterEnabled());
        }

        Map<String, String> report = G9SentinelClusterRemainderHonesty.honestSkipTemplate(
                G9SentinelClusterRemainderHonesty.proveStandaloneInventoryStub(), live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9SentinelClusterRemainderHonesty.verify(report);
        assertEquals("PASS", report.get("inventory_harness"));
        assertEquals("SKIP", report.get("sentinel_failover"));
        assertEquals("SKIP", report.get("cluster_failover"));
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("false", report.get("g9_83_100_121_128_148_161_174_redone"));
        assertEquals("false", report.get("policy_snapshot_redesigned"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertTrue(report.get("leftover_rows").contains("g9-sentinel-cluster-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-live-multi-jvm-replica"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-honesty"));
        assertTrue(report.get("leftover_rows").contains("g3-outbox-consume-crash-honesty"));
        assertTrue(report.get("leftover_rows").contains("g5-mid-publish-failure-honesty"));
        assertTrue(report.get("leftover_rows").contains("g10-leftover-honesty"));
        assertTrue(report.get("leftover_rows").contains("g4-export-wizard-deny-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-h5-every-page-form-write-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-remainder"));
        assertTrue(report.get("leftover_rows").contains("g17-mfa-recovery-vue-otp-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-ops-pg-dump-honesty"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G9SentinelClusterRemainderHonesty.verify(
                G9SentinelClusterRemainderHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeSentinelPass() {
        Map<String, String> fake = new HashMap<>(
                G9SentinelClusterRemainderHonesty.parseReport(fixture("fake-sentinel-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("sentinel_failover"));
    }

    @Test
    void honestyGateRejectsFakeClusterPass() {
        Map<String, String> fake = new HashMap<>(
                G9SentinelClusterRemainderHonesty.parseReport(fixture("fake-cluster-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("cluster_failover"));
    }

    @Test
    void honestyGateRejectsG9CloseAndSiblingRedos() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterRemainderHonesty.verify(
                        G9SentinelClusterRemainderHonesty.parseReport(fixture("fake-g9-close.env"))));
        assertTrue(close.getMessage().contains("g9_close"));

        Map<String, String> fake = new HashMap<>(
                G9SentinelClusterRemainderHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g9_83_100_121_128_148_161_174_redone", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterRemainderHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("g9_83_100_121_128_148_161_174_redone"));

        fake.put("g9_83_100_121_128_148_161_174_redone", "false");
        fake.put("leftover_rows", "g9-sentinel-cluster-honesty");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterRemainderHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void leftoverDocsKeepSiblingRowsIncludingG3AndG5() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : G9SentinelClusterRemainderHonesty.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertTrue(readme.contains("[g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g9 closed"));
        assertFalse(readme.contains("产品 G9 已关闭"));

        String leftover = readExisting(repoFile("docs/g9-sentinel-cluster-remainder-honesty.md"));
        assertTrue(leftover.contains("Keep **all** sibling leftover rows")
                || leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("#148"));
        assertTrue(leftover.contains("#161"));
        assertTrue(leftover.contains("#174"));
        assertTrue(leftover.contains("#195"));
        assertTrue(leftover.contains("#196"));
        assertTrue(leftover.contains("#202"));
        assertTrue(leftover.contains("#203"));
        assertTrue(leftover.contains("#204"));
        assertTrue(leftover.contains("#197"));
        assertTrue(leftover.contains("#205"));
        assertTrue(leftover.contains("#211"));
        assertTrue(leftover.contains("#188"));
        assertTrue(leftover.contains("#214"));
        assertTrue(leftover.contains("does **not** claim a product G9 close")
                || leftover.contains("Does **not** claim a product G9 close")
                || leftover.contains("does **not** claim product G9 close"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g9 is closed"));
        assertFalse(leftover.contains("产品 G9 已关闭"));
    }

    @Test
    void claimListG9RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g9-sentinel-cluster-honesty.md"));
        assertTrue(text.contains("g9-live-multi-jvm-replica.md"));
        assertTrue(text.contains("g9-sentinel-cluster-remainder-honesty.md"));
        assertTrue(text.contains("g9-redis-down-invalidate-honesty.md"));
        assertTrue(text.contains("g3-outbox-consume-crash-honesty.md"));
        assertTrue(text.contains("g5-mid-publish-failure-honesty.md"));
        assertTrue(text.contains("g10-leftover-honesty.md"));
        assertTrue(text.contains("g4-export-wizard-deny-honesty.md"));
        assertTrue(text.contains("g2-h5-every-page-form-write-honesty.md"));
        assertTrue(text.contains("g9-redis-down-invalidate-remainder.md"));
        assertTrue(text.contains("g17-mfa-recovery-vue-otp-honesty.md"));
        assertTrue(text.contains("g2-ops-pg-dump-honesty.md"));
        assertTrue(text.contains("#148"));
        assertTrue(text.contains("#161"));
        assertTrue(text.contains("不宣称产品 G9 关闭") || text.contains("不宣称 Sentinel/Cluster"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G9 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-sentinel-cluster-remainder-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-sentinel-cluster-remainder-honesty/fixtures/" + name);
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
