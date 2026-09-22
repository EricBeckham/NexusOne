package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Thin honesty leftover after #123. Proves the 90-day / 365-day window
 * policy in-memory. Live {@code :5432} / JAR / Quartz / MinIO remaining
 * window stays SKIP unless observed. Does not redo #123 Job/IT suites
 * and does not claim product G4 close.
 */
class G4HotPurge90dHonestyTest {

    @Test
    void inMemoryWindowPolicyPassesWithoutDroppingHotTable() {
        G4HotPurge90dHonesty.WindowPolicy window = G4HotPurge90dHonesty.proveWindowPolicy();
        assertEquals(Duration.ofDays(90), window.hotRetention());
        assertEquals(Duration.ofDays(365), window.archiveRetention());
        assertFalse(window.youngEligible());
        assertTrue(window.archived91DayEligible());
        assertFalse(window.archiveOnly366EligibleWithoutCold());
        assertFalse(window.dropTable());
        assertEquals("PASS", window.verdict());
    }

    @Test
    void liveProbeCannotClaimLivePurgeWindow() {
        G4HotPurge90dHonesty.LiveInventory live = G4HotPurge90dHonesty.probeLive();
        assertEquals("SKIP", live.livePurgeWindow());
        assertEquals("SKIP", live.liveQuartz());
        assertEquals("SKIP", live.liveMinioCold());
        assertFalse(live.livePurgeSkipReason().isBlank());
        assertFalse(live.liveQuartzSkipReason().isBlank());
        assertFalse(live.liveMinioSkipReason().isBlank());
        if (!live.liveDbPresent() || !live.liveJarPresent()) {
            assertFalse(live.livePurgeObserved());
        }

        Map<String, String> report = G4HotPurge90dHonesty.honestSkipTemplate(
                G4HotPurge90dHonesty.proveWindowPolicy(), live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G4HotPurge90dHonesty.verify(report);
        assertEquals("PASS", report.get("window_policy"));
        assertEquals("SKIP", report.get("live_purge_window"));
        assertEquals("NOT_CLAIMED", report.get("g4_close"));
        assertEquals("false", report.get("g4_123_136_147_152_156_166_redone"));
        assertEquals("false", report.get("hot_table_dropped"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G4HotPurge90dHonesty.verify(G4HotPurge90dHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePurgePass() {
        Map<String, String> fake = new HashMap<>(
                G4HotPurge90dHonesty.parseReport(fixture("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4HotPurge90dHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_purge_window"));
    }

    @Test
    void honestyGateRejectsG4CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G4HotPurge90dHonesty.parseReport(fixture("fake-g4-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4HotPurge90dHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G4HotPurge90dHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g4_123_136_147_152_156_166_redone", "true");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4HotPurge90dHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_123_136_147_152_156_166_redone"));
    }

    @Test
    void honestyGateRejectsHotTableDrop() {
        Map<String, String> fake = new HashMap<>(
                G4HotPurge90dHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("hot_table_dropped", "true");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4HotPurge90dHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("hot_table_dropped"));
    }

    @Test
    void v504SqlGrantsDeleteAndDoesNotDropHotTable() throws IOException {
        String sql = readExisting(repoFile("nexus-audit-core/src/main/resources/db/migration/V504__audit_hot_purge.sql"));
        String upper = sql.toUpperCase(Locale.ROOT);
        assertTrue(upper.contains("GRANT DELETE"));
        assertTrue(upper.contains("PLATFORM.AUDIT_RECORD"));
        assertFalse(upper.contains("DROP TABLE"));
        assertFalse(upper.contains("GRANT UPDATE"));
    }

    @Test
    void leftoverDocsKeepBothG4PurgeRowsAndSiblings() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[g4-hot-purge-90d](./g4-hot-purge-90d.md)"));
        assertTrue(readme.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(readme.contains("g4-export-wizard-deny"));
        assertTrue(readme.contains("g4-p1-staging-audit-events"));
        assertTrue(readme.contains("g4-pkce-spa-verifier"));
        assertTrue(readme.contains("g4-export-wizard"));
        assertTrue(readme.contains("g4-pkce-spa-verifier-honesty"));
        assertTrue(readme.contains("g15-native-bridge-honesty"));
        assertTrue(readme.contains("g10-identity-drift-honesty"));
        assertTrue(readme.contains("g1-fieldpolicy-mask-second-auth-honesty"));
        assertTrue(readme.contains("g6-recovery-port-honesty"));
        assertTrue(readme.contains("g7-withdraw-browser-honesty"));
        assertTrue(readme.contains("g9-redis-down-invalidate-honesty"));
        assertTrue(readme.contains("g5-browser-failed-publish"));
        assertTrue(readme.contains("g5-mid-publish-failure"));
        assertTrue(readme.contains("s0-secret-getenv-snapshot-honesty"));
        assertTrue(readme.contains("s0-secret-rotate-without-restart"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g4 closed"));
        assertFalse(readme.contains("产品 G4 已关闭"));

        String leftover = readExisting(repoFile("docs/g4-hot-purge-90d-honesty.md"));
        assertTrue(leftover.contains("Keep both leftover rows"));
        assertTrue(leftover.contains("#123"));
        assertTrue(leftover.contains("does **not** claim a product-wide G4 close")
                || leftover.contains("Does **not** claim a product-wide G4 close"));
        assertTrue(leftover.contains("does **not** redo #123") || leftover.contains("Do not redo #123"));
        assertTrue(leftover.contains("does **not** DROP") || leftover.contains("Do **not** DROP"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g4 is closed"));
        assertFalse(leftover.contains("产品 G4 已关闭"));
    }

    @Test
    void claimListG4RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g4-hot-purge-90d.md"));
        assertTrue(text.contains("g4-hot-purge-90d-honesty.md"));
        assertTrue(text.contains("#123"));
        assertTrue(text.contains("不要重做 #123"));
        assertTrue(text.contains("不要宣称产品 G4 关闭"));
        assertTrue(text.contains("g4-export-wizard-deny.md"));
        assertTrue(text.contains("g4-p1-staging-audit-events.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier-honesty.md"));
        assertTrue(text.contains("g15-native-bridge-honesty.md"));
        assertTrue(text.contains("g10-identity-drift-honesty.md"));
        assertTrue(text.contains("g1-fieldpolicy-mask-second-auth-honesty.md"));
        assertTrue(text.contains("g7-withdraw-browser-honesty.md"));
        assertTrue(text.contains("g5-browser-failed-publish.md"));
        assertTrue(text.contains("s0-secret-getenv-snapshot-honesty.md"));
        assertFalse(text.contains("产品 G4 已关闭"));
        assertTrue(text.contains("不要重开 G18"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g4-hot-purge-90d-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g4-hot-purge-90d-honesty/fixtures/" + name);
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
