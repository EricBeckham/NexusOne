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
import one.nexus.permission.redis.G9RedisDownInvalidateRemainder;
import one.nexus.permission.redis.G9RedisDownInvalidateRemainder.DeadClientHarness;
import one.nexus.permission.redis.G9RedisDownInvalidateRemainder.LiveInventory;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover remainder beyond #174. Proves the dead-client harness
 * PASSes. Live Redis process-down / live invalidate stay SKIP unless
 * observed. Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174
 * suites and does not claim product G9 close.
 */
class G9RedisDownInvalidateRemainderTest {

    @Test
    void deadClientHarnessPassesAndIsNotLiveRedisDown() {
        DeadClientHarness harness = G9RedisDownInvalidateRemainder.proveDeadClientHarness();
        assertEquals(G9RedisDownInvalidateRemainder.DEAD_CLIENT_PORT, harness.port());
        assertTrue(harness.notifyFailed());
        assertFalse(harness.remoteSuccessClaimed());
        assertTrue(harness.remoteFailureCount() >= 1);
        assertFalse(harness.published());
        assertFalse(harness.versionHintWritten());
        assertEquals("PASS", harness.verdict());
        assertTrue(harness.deadClientIsNotLiveDown());
        assertFalse(G9RedisDownInvalidateRemainder.portOpen(
                G9RedisDownInvalidateRemainder.HOST, G9RedisDownInvalidateRemainder.DEAD_CLIENT_PORT));
    }

    @Test
    void listeningRedisWithoutObservedDownOrInvalidateStaysSkip() {
        LiveInventory inventory = G9RedisDownInvalidateRemainder.fromLivePing("master");
        assertTrue(inventory.redisPresent());
        assertEquals("master", inventory.redisRole());
        assertFalse(inventory.redisDownObserved());
        assertFalse(inventory.liveInvalidateObserved());
        assertEquals("SKIP", inventory.liveRedisDown());
        assertEquals("SKIP", inventory.liveInvalidate());
        assertTrue(inventory.liveRedisDownSkipReason().contains("dead-client harness PASS"));
        assertTrue(inventory.liveInvalidateSkipReason().contains("live PUBLISH/SUBSCRIBE"));
    }

    @Test
    void liveProbeCannotClaimLiveRedisDownOrInvalidate() {
        DeadClientHarness harness = G9RedisDownInvalidateRemainder.proveDeadClientHarness();
        LiveInventory inventory = G9RedisDownInvalidateRemainder.probeLive();
        assertEquals("PASS", harness.verdict());
        assertEquals("SKIP", inventory.liveRedisDown());
        assertEquals("SKIP", inventory.liveInvalidate());
        assertFalse(inventory.liveRedisDownSkipReason().isBlank());
        assertFalse(inventory.liveInvalidateSkipReason().isBlank());
        if (!inventory.redisPresent()) {
            assertFalse(inventory.redisDownObserved());
            assertFalse(inventory.liveInvalidateObserved());
            assertTrue(inventory.liveRedisDownSkipReason().contains("dead-client harness PASS"));
        }

        Map<String, String> report = G9RedisDownInvalidateRemainder.honestSkipTemplate(harness, inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9RedisDownInvalidateRemainder.verify(report);
        assertEquals("PASS", report.get("dead_client_harness"));
        assertEquals("SKIP", report.get("live_redis_down"));
        assertEquals("SKIP", report.get("live_invalidate"));
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("false", report.get("g9_83_100_121_128_148_161_174_redone"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("true", report.get("leftover_rows_kept"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G9RedisDownInvalidateRemainder.verify(
                G9RedisDownInvalidateRemainder.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveRedisDownPass() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateRemainder.parseReport(fixture("fake-live-redis-down-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateRemainder.verify(fake));
        assertTrue(ex.getMessage().contains("live_redis_down"));
    }

    @Test
    void honestyGateRejectsFakeLiveInvalidatePass() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateRemainder.parseReport(fixture("fake-live-invalidate-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateRemainder.verify(fake));
        assertTrue(ex.getMessage().contains("live_invalidate"));
    }

    @Test
    void honestyGateRejectsG9CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateRemainder.parseReport(fixture("fake-g9-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateRemainder.verify(fake));
        assertTrue(ex.getMessage().contains("g9_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateRemainder.parseReport(fixture("honest-skip.env")));
        fake.put("g9_83_100_121_128_148_161_174_redone", "true");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateRemainder.verify(fake));
        assertTrue(ex.getMessage().contains("g9_83_100_121_128_148_161_174_redone"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndDoNotCloseG9() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(G9RedisDownInvalidateRemainder.leftoverRowsKept(readme));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g9 closed"));
        assertFalse(readme.contains("产品 G9 已关闭"));

        String leftover = readExisting(repoFile("docs/g9-redis-down-invalidate-remainder.md"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("#174"));
        assertTrue(leftover.contains("g9-policysnapshot-invalidate"));
        assertTrue(leftover.contains("g9-failover-remainder"));
        assertTrue(leftover.contains("g9-redis-down-invalidate-honesty"));
        assertTrue(leftover.contains("s0s1-live-redis-minio-isolation-honesty"));
        assertTrue(leftover.contains("g6-recovery-port-dry-run-honesty"));
        assertTrue(leftover.contains("does **not** claim") || leftover.contains("Does **not** claim"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g9 is closed"));
        assertFalse(leftover.contains("产品 G9 已关闭"));
    }

    @Test
    void claimListG9RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g9-redis-down-invalidate-honesty.md"));
        assertTrue(text.contains("g9-redis-down-invalidate-remainder.md"));
        assertTrue(text.contains("g9-policysnapshot-invalidate.md"));
        assertTrue(text.contains("g9-failover-remainder.md"));
        assertTrue(text.contains("#174"));
        assertTrue(text.contains("不要宣称产品 G9 关闭"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G9 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-redis-down-invalidate-remainder/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-redis-down-invalidate-remainder/fixtures/" + name);
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
