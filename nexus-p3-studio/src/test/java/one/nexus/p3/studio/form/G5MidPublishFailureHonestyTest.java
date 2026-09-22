package one.nexus.p3.studio.form;

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
 * G5 leftover beyond #145 / #171: honesty inventory for mid-publish
 * abort-after-SchemaService (always-green harness PASS) and a live
 * browser failed-publish click (honest SKIP). Does not redo #94 evolve
 * DDL abort, #145 Zonky ITs, or #171 leftover. Does not claim product
 * G5 close.
 */
class G5MidPublishFailureHonestyTest {

    @Test
    void midPublishHarnessPassesAndLiveBrowserStaysSkip() {
        G5MidPublishFailureHonesty.MidPublishHarness harness =
                G5MidPublishFailureHonesty.proveMidPublishHarness();
        assertEquals("PASS", harness.verdict());
        assertTrue(harness.runningPointerUnchanged());
        assertTrue(harness.snapshotUnchanged());
        assertTrue(harness.rolledBack());
        assertTrue(harness.noV2Version());
        assertTrue(harness.cause().contains("mid-publish abort after SchemaService"));

        G5MidPublishFailureHonesty.LiveInventory live = G5MidPublishFailureHonesty.probeLive();
        assertEquals("SKIP", live.browserFailedPublish());
        assertFalse(live.skipReason().isBlank());
        assertFalse(live.clickObserved());

        Map<String, String> report = G5MidPublishFailureHonesty.honestSkipTemplate(harness, live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G5MidPublishFailureHonesty.verify(report);
        assertEquals("PASS", report.get("mid_publish_harness"));
        assertEquals("SKIP", report.get("browser_failed_publish"));
        assertEquals("NOT_CLAIMED", report.get("g5_close"));
        assertEquals("false", report.get("redone_94"));
        assertEquals("false", report.get("redone_145"));
        assertEquals("false", report.get("redone_171"));
        assertEquals("false", report.get("simulate_publish_log_is_fail_click"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertTrue(report.get("leftover_rows_kept").contains("g5-mid-publish-failure"));
        assertTrue(report.get("leftover_rows_kept").contains("g5-browser-failed-publish"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G5MidPublishFailureHonesty.verify(
                G5MidPublishFailureHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeBrowserPass() {
        Map<String, String> fake = new HashMap<>(
                G5MidPublishFailureHonesty.parseReport(fixture("fake-browser-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G5MidPublishFailureHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("browser_failed_publish"));
    }

    @Test
    void honestyGateRejectsG5CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G5MidPublishFailureHonesty.parseReport(fixture("fake-g5-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G5MidPublishFailureHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g5_close"));
    }

    @Test
    void honestyGateRejectsRedoOf94And145And171() {
        Map<String, String> fake = new HashMap<>(
                G5MidPublishFailureHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redone_94", "true");
        IllegalArgumentException redo94 = assertThrows(IllegalArgumentException.class,
                () -> G5MidPublishFailureHonesty.verify(fake));
        assertTrue(redo94.getMessage().contains("redone_94"));

        fake.put("redone_94", "false");
        fake.put("redone_145", "true");
        IllegalArgumentException redo145 = assertThrows(IllegalArgumentException.class,
                () -> G5MidPublishFailureHonesty.verify(fake));
        assertTrue(redo145.getMessage().contains("redone_145"));

        fake.put("redone_145", "false");
        fake.put("redone_171", "true");
        IllegalArgumentException redo171 = assertThrows(IllegalArgumentException.class,
                () -> G5MidPublishFailureHonesty.verify(fake));
        assertTrue(redo171.getMessage().contains("redone_171"));
    }

    @Test
    void leftoverDocsKeepBothG5RowsAndSiblings() throws IOException {
        String leftover = readExisting(repoFile("docs/g5-mid-publish-failure-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#145"));
        assertTrue(leftover.contains("#171"));
        assertTrue(leftover.contains("SchemaServicePublishHook"));
        assertTrue(leftover.contains("Keep both leftover rows") || leftover.contains("Keep BOTH leftover rows"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows"));
        assertTrue(leftover.contains("g5-mid-publish-failure"));
        assertTrue(leftover.contains("g5-browser-failed-publish"));
        assertTrue(leftover.contains("g6-recovery-port-dry-run-honesty"));
        assertTrue(leftover.contains("does **not** redo #94") || leftover.contains("Does **not** redo #94"));
        assertTrue(leftover.contains("does **not** redo #145") || leftover.contains("Does **not** redo #145"));
        assertTrue(leftover.contains("does **not** redo #171") || leftover.contains("Does **not** redo #171"));
        assertFalse(leftover.contains("产品 G5 已关闭"));
        assertFalse(lower.contains("product g5 closed"));
        assertFalse(lower.contains("reopen g18"));

        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[g5-mid-publish-failure](./g5-mid-publish-failure.md)"));
        assertTrue(readme.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(readme.contains("[g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-honesty](./g6-recovery-port-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run](./g6-recovery-port-dry-run.md)"));
        assertTrue(readme.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(readme.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(readme.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(readme.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(readme.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(readme.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(readme.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(readme.contains("[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)"));
        assertTrue(readme.contains("[g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md)"));
        assertTrue(readme.contains("[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)"));
        assertTrue(readme.contains("[g2-form-write-honesty](./g2-form-write-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g5 closed"));
        assertFalse(readme.contains("产品 G5 已关闭"));
    }

    @Test
    void claimListG5RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g5-mid-publish-failure.md"));
        assertTrue(text.contains("g5-browser-failed-publish.md"));
        assertTrue(text.contains("g5-mid-publish-failure-honesty.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run-honesty.md"));
        assertTrue(text.contains("#145"));
        assertTrue(text.contains("#171"));
        assertTrue(text.contains("不要重做 #94"));
        assertTrue(text.contains("不要重做 #145") || text.contains("不要重做 #94 evolve DDL abort / #145"));
        assertTrue(text.contains("不要宣称产品 G5 关闭"));
        assertFalse(text.contains("产品 G5 已关闭"));
        assertTrue(text.contains("不要重开 G18"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g5-mid-publish-failure-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g5-mid-publish-failure-honesty/fixtures/" + name);
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
