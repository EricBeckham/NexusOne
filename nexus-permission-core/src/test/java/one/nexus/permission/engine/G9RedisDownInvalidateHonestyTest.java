package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import one.nexus.permission.redis.G9RedisDownInvalidateHonesty;
import one.nexus.permission.redis.G9RedisDownInvalidateHonesty.RedisInventory;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #121 / #148: honesty inventory for live Redis-down
 * and live PolicySnapshot invalidate. Dead client on port 1 is not a
 * live Redis process-down. Does not redo #100 / #121 / #128 / #148 / #161
 * suites and does not claim product G9 close.
 */
class G9RedisDownInvalidateHonestyTest {

    @Test
    void listeningRedisWithoutObservedDownOrInvalidateStaysSkip() {
        RedisInventory inventory = G9RedisDownInvalidateHonesty.fromLivePing("master");
        assertTrue(inventory.redisPresent());
        assertEquals("master", inventory.redisRole());
        assertFalse(inventory.redisDownObserved());
        assertFalse(inventory.liveInvalidateObserved());
        assertEquals("SKIP", inventory.liveRedisDown());
        assertEquals("SKIP", inventory.liveInvalidate());
        assertTrue(inventory.deadClientIsNotLiveDown());
        assertEquals(1, inventory.simulatedDeadClientPort());
        assertTrue(inventory.liveRedisDownSkipReason().contains("dead client on port 1"));
        assertTrue(inventory.liveInvalidateSkipReason().contains("live PUBLISH/SUBSCRIBE"));
    }

    @Test
    void liveProbeCannotClaimLiveRedisDownOrInvalidate() {
        RedisInventory inventory = G9RedisDownInvalidateHonesty.probeLive();
        assertEquals("SKIP", inventory.liveRedisDown());
        assertEquals("SKIP", inventory.liveInvalidate());
        assertTrue(inventory.deadClientIsNotLiveDown());
        assertEquals(1, inventory.simulatedDeadClientPort());
        assertFalse(inventory.liveRedisDownSkipReason().isBlank());
        assertFalse(inventory.liveInvalidateSkipReason().isBlank());
        if (!inventory.redisPresent()) {
            assertFalse(inventory.redisDownObserved());
            assertFalse(inventory.liveInvalidateObserved());
            assertTrue(inventory.liveRedisDownSkipReason().contains("dead client on port 1"));
        }

        Map<String, String> report = G9RedisDownInvalidateHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9RedisDownInvalidateHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("false", report.get("g9_100_121_128_148_161_redone"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G9RedisDownInvalidateHonesty.verify(G9RedisDownInvalidateHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveRedisDownPass() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateHonesty.parseReport(fixture("fake-live-redis-down-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_redis_down"));
    }

    @Test
    void honestyGateRejectsFakeLiveInvalidatePass() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateHonesty.parseReport(fixture("fake-live-invalidate-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_invalidate"));
    }

    @Test
    void honestyGateRejectsG9CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateHonesty.parseReport(fixture("fake-g9-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g9_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G9RedisDownInvalidateHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g9_100_121_128_148_161_redone", "true");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9RedisDownInvalidateHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g9_100_121_128_148_161_redone"));
    }

    @Test
    void deadClientPortOneIsNotLiveRedisDown() {
        RedisInventory inventory = G9RedisDownInvalidateHonesty.probeLive();
        assertEquals(G9RedisDownInvalidateHonesty.DEAD_CLIENT_PORT, inventory.simulatedDeadClientPort());
        assertTrue(inventory.deadClientIsNotLiveDown());
        assertEquals("SKIP", inventory.liveRedisDown());
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-redis-down-invalidate-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-redis-down-invalidate-honesty/fixtures/" + name);
    }
}
