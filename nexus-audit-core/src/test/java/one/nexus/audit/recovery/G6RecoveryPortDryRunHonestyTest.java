package one.nexus.audit.recovery;

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
import org.junit.jupiter.api.Test;

/**
 * G6 leftover beyond #135 / #164: honest PASS/SKIP for RecoveryPort still
 * dry-run / not product HA. Does not redo the #135 Zonky mutation ITs or
 * the #164 thin honesty suite. Does not claim product G6 / HA close.
 */
class G6RecoveryPortDryRunHonestyTest {

    @Test
    void dryRunContractStaysPassAndLiveHaRpoStaySkip() {
        G6RecoveryPortDryRunHonesty.Inventory inventory = G6RecoveryPortDryRunHonesty.probeDryRun();
        assertEquals("PASS", inventory.dryRunContract());
        assertFalse(inventory.executed());
        assertFalse(inventory.profileAccepted());
        assertTrue("DRY_RUN".equals(inventory.dryRunStatus()) || "TOOL_MISSING".equals(inventory.dryRunStatus()));
        assertEquals("SKIP", inventory.minuteLevelRpo());
        assertEquals("SKIP", inventory.productHa());
        assertEquals("SKIP", inventory.executingRestore());
        assertTrue(inventory.dryRunIsNotProductHa());
        assertTrue(inventory.dryRunIsNotMinuteRpo());
        assertFalse(inventory.minuteRpoSkipReason().isBlank());
        assertFalse(inventory.productHaSkipReason().isBlank());
        assertTrue(inventory.minuteRpoSkipReason().contains("haopan_platform"));
        assertTrue(inventory.productHaSkipReason().contains("not product HA"));

        Map<String, String> report = G6RecoveryPortDryRunHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G6RecoveryPortDryRunHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g6_close"));
        assertEquals("NOT_CLAIMED", report.get("recovery_port_close"));
        assertEquals("false", report.get("redone_135"));
        assertEquals("false", report.get("redone_164"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("false", report.get("haopan_platform_touched"));
        assertEquals("false", report.get("product_db_mutated"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertTrue(report.get("leftover_rows_kept").contains("g6-recovery-port-dry-run"));
        assertTrue(report.get("leftover_rows_kept").contains("g6-recovery-port-honesty"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G6RecoveryPortDryRunHonesty.verify(
                G6RecoveryPortDryRunHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeMinuteRpoPass() {
        Map<String, String> fake = new HashMap<>(
                G6RecoveryPortDryRunHonesty.parseReport(fixture("fake-minute-rpo-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G6RecoveryPortDryRunHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("minute_level_rpo"));
    }

    @Test
    void honestyGateRejectsFakeProductHaPass() {
        Map<String, String> fake = new HashMap<>(
                G6RecoveryPortDryRunHonesty.parseReport(fixture("fake-product-ha-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G6RecoveryPortDryRunHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("product_ha"));
    }

    @Test
    void honestyGateRejectsG6CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G6RecoveryPortDryRunHonesty.parseReport(fixture("fake-g6-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G6RecoveryPortDryRunHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g6_close"));
    }

    @Test
    void honestyGateRejectsRedoOf135And164() {
        Map<String, String> fake = new HashMap<>(
                G6RecoveryPortDryRunHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redone_135", "true");
        IllegalArgumentException redo135 = assertThrows(IllegalArgumentException.class,
                () -> G6RecoveryPortDryRunHonesty.verify(fake));
        assertTrue(redo135.getMessage().contains("redone_135"));

        fake.put("redone_135", "false");
        fake.put("redone_164", "true");
        IllegalArgumentException redo164 = assertThrows(IllegalArgumentException.class,
                () -> G6RecoveryPortDryRunHonesty.verify(fake));
        assertTrue(redo164.getMessage().contains("redone_164"));
    }

    @Test
    void leftoverDocsKeepBothRowsAndStayHonest() throws IOException {
        String leftover = readExisting(repoFile("docs/g6-recovery-port-dry-run-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#135"));
        assertTrue(leftover.contains("#164"));
        assertTrue(leftover.contains("dry-run"));
        assertTrue(lower.contains("minute-level rpo") || leftover.contains("分钟级 RPO"));
        assertTrue(lower.contains("product ha") || leftover.contains("产品 HA"));
        assertTrue(leftover.contains("Keep both leftover rows") || leftover.contains("Keep BOTH leftover rows"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows"));
        assertTrue(leftover.contains("g6-recovery-port-dry-run"));
        assertTrue(leftover.contains("g6-recovery-port-honesty"));
        assertTrue(leftover.contains("does **not** redo #135") || leftover.contains("Does **not** redo #135"));
        assertTrue(leftover.contains("does **not** redo #164") || leftover.contains("Does **not** redo #164"));
        assertFalse(leftover.contains("产品 RecoveryPort 已关闭"));
        assertFalse(leftover.contains("产品 G6 已关闭"));
        assertFalse(lower.contains("product g6 closed"));
        assertFalse(lower.contains("product ha closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run](./g6-recovery-port-dry-run.md)"));
        assertTrue(readme.contains("[g6-recovery-port-honesty](./g6-recovery-port-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertTrue(readme.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(readme.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(readme.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(readme.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(readme.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(readme.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(readme.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(readme.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(readme.contains("[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product recoveryport closed"));
        assertFalse(readme.contains("产品 G6 已关闭"));
    }

    @Test
    void claimListKeepsBothLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run.md"));
        assertTrue(text.contains("g6-recovery-port-honesty.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run-honesty.md"));
        assertTrue(text.contains("#135"));
        assertTrue(text.contains("不要重做 #135"));
        assertTrue(text.contains("分钟级 RPO"));
        assertTrue(text.contains("交付级 HA"));
        assertTrue(text.contains("不要宣称产品 `RecoveryPort`"));
        assertFalse(text.contains("产品 RecoveryPort 已关闭"));
        assertFalse(text.contains("产品 G6 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g6-recovery-port-dry-run-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g6-recovery-port-dry-run-honesty/fixtures/" + name);
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
