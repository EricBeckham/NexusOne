package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import one.nexus.permission.redis.G9LiveMultiJvmReplicaHonesty;
import one.nexus.permission.redis.G9LiveMultiJvmReplicaHonesty.JvmInventory;
import one.nexus.permission.redis.G9LiveMultiJvmReplicaHonesty.ReplicaInventory;
import one.nexus.permission.redis.Redis32Client;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #148: honesty inventory for live multi-JVM and
 * replica promotion. Does not redo Sentinel / Cluster inventory, does
 * not redo #89 / #83 / #100 / #121 / #128 dual-reader fail-close, and
 * does not claim product G9 / Sentinel / Cluster close.
 */
class G9LiveMultiJvmReplicaHonestyTest {

    @Test
    void standaloneReplicationInfoIsNotReplicaPromotion() {
        String replication = "# Replication\r\nrole:master\r\nconnected_slaves:0\r\n";
        ReplicaInventory inventory = G9LiveMultiJvmReplicaHonesty.fromReplicationInfo(replication);
        assertEquals("master", inventory.redisRole());
        assertEquals(0, inventory.connectedSlaves());
        assertFalse(inventory.replicaPresent());
        assertFalse(inventory.promotionObserved());
        assertEquals("SKIP", inventory.replicaPromotion());
        assertTrue(inventory.skipReason().contains("replica promotion not observed"));
        assertEquals("0", Redis32Client.infoField(replication, "connected_slaves"));
    }

    @Test
    void replicaPresentWithoutObservedPromotionStaysSkip() {
        String replication = "# Replication\r\nrole:master\r\nconnected_slaves:1\r\n"
                + "slave0:ip=127.0.0.1,port=6380,state=online\r\n";
        ReplicaInventory inventory = G9LiveMultiJvmReplicaHonesty.fromReplicationInfo(replication);
        assertTrue(inventory.replicaPresent());
        assertFalse(inventory.promotionObserved());
        assertEquals("SKIP", inventory.replicaPromotion());
        assertTrue(inventory.skipReason().contains("promotion was not observed"));
    }

    @Test
    void slaveRoleWithoutObservedPromotionStaysSkip() {
        String replication = "# Replication\r\nrole:slave\r\nmaster_host:127.0.0.1\r\n"
                + "master_port:6379\r\nconnected_slaves:0\r\n";
        ReplicaInventory inventory = G9LiveMultiJvmReplicaHonesty.fromReplicationInfo(replication);
        assertTrue(inventory.replicaPresent());
        assertEquals("SKIP", inventory.replicaPromotion());
    }

    @Test
    void liveProbeCannotClaimReplicaOrSecondJvm() {
        ReplicaInventory replica = G9LiveMultiJvmReplicaHonesty.probeReplica();
        JvmInventory jvms = G9LiveMultiJvmReplicaHonesty.probeJvms();
        assertEquals("SKIP", replica.replicaPromotion());
        assertEquals("SKIP", jvms.liveMultiJvm());
        assertFalse(replica.skipReason().isBlank());
        assertFalse(jvms.skipReason().isBlank());
        assertFalse(jvms.multiJvmObserved());
        if (!replica.redisPresent()) {
            assertFalse(replica.replicaPresent());
            assertFalse(replica.promotionObserved());
        }

        Map<String, String> report = G9LiveMultiJvmReplicaHonesty.honestSkipTemplate(replica, jvms);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9LiveMultiJvmReplicaHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("NOT_CLAIMED", report.get("sentinel_cluster_close"));
        assertEquals("false", report.get("g9_89_83_100_121_128_148_redone"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G9LiveMultiJvmReplicaHonesty.verify(G9LiveMultiJvmReplicaHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeReplicaPass() {
        Map<String, String> fake = new HashMap<>(G9LiveMultiJvmReplicaHonesty.parseReport(fixture("fake-replica-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("replica_promotion"));
    }

    @Test
    void honestyGateRejectsFakeMultiJvmPass() {
        Map<String, String> fake = new HashMap<>(G9LiveMultiJvmReplicaHonesty.parseReport(fixture("fake-multi-jvm-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_multi_jvm"));
    }

    @Test
    void honestyGateRejectsSentinelClusterProductClose() {
        Map<String, String> fake = new HashMap<>(G9LiveMultiJvmReplicaHonesty.parseReport(
                fixture("fake-sentinel-cluster-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("sentinel_cluster_close"));
    }

    @Test
    void honestyGateRejectsG9CloseClaim() {
        Map<String, String> fake = new HashMap<>(G9LiveMultiJvmReplicaHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g9_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9LiveMultiJvmReplicaHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g9_close"));
    }

    @Test
    void inProcessDualReaderIsNotLiveMultiJvm() {
        JvmInventory jvms = G9LiveMultiJvmReplicaHonesty.probeJvms();
        assertEquals("SKIP", jvms.liveMultiJvm());
        assertTrue(jvms.skipReason().contains("surefire") || jvms.skipReason().contains("one application JVM"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-live-multi-jvm-replica/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-live-multi-jvm-replica/fixtures/" + name);
    }
}
