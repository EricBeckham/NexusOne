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
import one.nexus.permission.redis.G9LiveMultiJvmReplicaRemainderHonesty;
import one.nexus.permission.redis.G9LiveMultiJvmReplicaRemainderHonesty.InventoryHarness;
import one.nexus.permission.redis.G9LiveMultiJvmReplicaRemainderHonesty.LiveObservation;
import one.nexus.permission.redis.Redis32Client;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #209 / #161 / #148: live multi-JVM / replica
 * promotion remainder honesty. Standalone INFO replication inventory
 * stub PASS; no second JVM / no replica is honest SKIP. Does not redo
 * #83/#100/#121/#128/#148/#161/#174/#209. Does not claim product G9 /
 * Sentinel / Cluster close. G18 stays closed.
 */
class G9LiveMultiJvmReplicaRemainderHonestyTest {

    @Test
    void standaloneReplicationInventoryStubPassesWithoutReplica() {
        InventoryHarness harness = G9LiveMultiJvmReplicaRemainderHonesty.proveStandaloneReplicationInventoryStub();
        assertEquals("master", harness.redisRole());
        assertEquals(0, harness.connectedSlaves());
        assertFalse(harness.replicaPresent());
        assertFalse(harness.surefireIsSecondJvm());
        assertEquals("PASS", harness.verdict());
        String replication = "# Replication\r\nrole:master\r\nconnected_slaves:0\r\n";
        assertEquals("0", Redis32Client.infoField(replication, "connected_slaves"));
    }

    @Test
    void liveProbeCannotClaimReplicaOrSecondJvm() {
        LiveObservation live = G9LiveMultiJvmReplicaRemainderHonesty.probeLive();
        assertEquals("SKIP", live.replicaPromotion());
        assertEquals("SKIP", live.liveMultiJvm());
        assertFalse(live.replicaSkipReason().isBlank());
        assertFalse(live.multiJvmSkipReason().isBlank());
        assertFalse(live.promotionObserved());
        assertFalse(live.multiJvmObserved());
        if (!live.redisPresent()) {
            assertFalse(live.replicaPresent());
        }
        assertTrue(live.multiJvmSkipReason().contains("surefire")
                || live.multiJvmSkipReason().contains("one application JVM"));

        Map<String, String> report = G9LiveMultiJvmReplicaRemainderHonesty.honestSkipTemplate(
                G9LiveMultiJvmReplicaRemainderHonesty.proveStandaloneReplicationInventoryStub(), live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9LiveMultiJvmReplicaRemainderHonesty.verify(report);
        assertEquals("PASS", report.get("inventory_harness"));
        assertEquals("SKIP", report.get("replica_promotion"));
        assertEquals("SKIP", report.get("live_multi_jvm"));
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("NOT_CLAIMED", report.get("sentinel_cluster_close"));
        assertEquals("false", report.get("g9_83_100_121_128_148_161_174_209_redone"));
        assertEquals("false", report.get("policy_snapshot_redesigned"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("true", report.get("leftover_rows_kept"));
        for (String row : G9LiveMultiJvmReplicaRemainderHonesty.REQUIRED_LEFTOVER_ROWS) {
            assertTrue(report.get("leftover_rows").contains(row), "missing leftover_rows " + row);
        }
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G9LiveMultiJvmReplicaRemainderHonesty.verify(
                G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeReplicaPass() {
        Map<String, String> fake = new HashMap<>(
                G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("fake-replica-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("replica_promotion"));
    }

    @Test
    void honestyGateRejectsFakeMultiJvmPass() {
        Map<String, String> fake = new HashMap<>(
                G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("fake-multi-jvm-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_multi_jvm"));
    }

    @Test
    void honestyGateRejectsG9CloseSentinelCloseAndSiblingRedos() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(
                        G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("fake-g9-close.env"))));
        assertTrue(close.getMessage().contains("g9_close"));

        IllegalArgumentException sentinel = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(
                        G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("fake-sentinel-cluster-close.env"))));
        assertTrue(sentinel.getMessage().contains("sentinel_cluster_close"));

        Map<String, String> fake = new HashMap<>(
                G9LiveMultiJvmReplicaRemainderHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g9_83_100_121_128_148_161_174_209_redone", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("g9_83_100_121_128_148_161_174_209_redone"));

        fake.put("g9_83_100_121_128_148_161_174_209_redone", "false");
        fake.put("leftover_rows", "g9-live-multi-jvm-replica");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaRemainderHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void leftoverDocsKeepSiblingRowsIncludingG9RemainderAndG15() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : G9LiveMultiJvmReplicaRemainderHonesty.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertTrue(readme.contains("[g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g9 closed"));
        assertFalse(readme.contains("产品 G9 已关闭"));

        String leftover = readExisting(repoFile("docs/g9-live-multi-jvm-replica-remainder-honesty.md"));
        assertTrue(leftover.contains("Keep **ALL** sibling leftover rows")
                || leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **all** sibling leftover rows"));
        assertTrue(leftover.contains("#148"));
        assertTrue(leftover.contains("#161"));
        assertTrue(leftover.contains("#209"));
        assertTrue(leftover.contains("#216"));
        assertTrue(leftover.contains("#218"));
        assertTrue(leftover.contains("#220"));
        assertTrue(leftover.contains("#222"));
        assertTrue(leftover.contains("#223"));
        assertTrue(leftover.contains("#224"));
        assertTrue(leftover.contains("#225"));
        assertTrue(leftover.contains("#221"));
        assertTrue(leftover.contains("#228"));
        assertTrue(leftover.contains("#226"));
        assertTrue(leftover.contains("g6-recovery-port-dry-run-remainder-honesty"));
        assertTrue(leftover.contains("g2-report-physical-dump-honesty"));
        assertTrue(leftover.contains("g10-leftover-honesty-remainder"));
        assertTrue(leftover.contains("g3-outbox-consume-idempotent-honesty"));
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
        assertTrue(text.contains("g9-live-multi-jvm-replica-remainder-honesty.md"));
        assertTrue(text.contains("g15-wx-dt-native-bridge-remainder.md"));
        assertTrue(text.contains("g17-separation-leftover-honesty.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier-remainder-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-getenv-remainder-honesty.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run-remainder-honesty.md"));
        assertTrue(text.contains("g2-report-physical-dump-honesty.md"));
        assertTrue(text.contains("g10-leftover-honesty-remainder.md"));
        assertTrue(text.contains("g3-outbox-consume-idempotent-honesty.md"));
        assertTrue(text.contains("g2-ops-pg-dump-honesty.md"));
        assertTrue(text.contains("#148"));
        assertTrue(text.contains("#161"));
        assertTrue(text.contains("#209"));
        assertTrue(text.contains("#216"));
        assertTrue(text.contains("#218"));
        assertTrue(text.contains("#222"));
        assertTrue(text.contains("#223"));
        assertTrue(text.contains("#224"));
        assertTrue(text.contains("#225"));
        assertTrue(text.contains("#221"));
        assertTrue(text.contains("#228"));
        assertTrue(text.contains("#226"));
        assertTrue(text.contains("不宣称产品 G9 关闭") || text.contains("不宣称 Sentinel/Cluster"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G9 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/" + name);
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
