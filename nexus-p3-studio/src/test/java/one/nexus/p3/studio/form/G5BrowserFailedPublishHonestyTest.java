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
import one.nexus.p3.studio.form.G5BrowserFailedPublishHonesty.LiveInventory;
import org.junit.jupiter.api.Test;

/**
 * G5 leftover beyond #94 / #145: honesty inventory for a browser
 * failed-publish click through {@link SchemaServicePublishHook} toward
 * real SchemaService DDL. Does not redo evolve abort or mid-publish
 * rollback suites. Does not claim product G5 close.
 */
class G5BrowserFailedPublishHonestyTest {

    @Test
    void liveProbeCannotClaimBrowserFailedPublish() {
        LiveInventory live = G5BrowserFailedPublishHonesty.probe();
        assertEquals("SKIP", live.browserFailedPublish());
        assertFalse(live.skipReason().isBlank());
        assertFalse(live.clickObserved());
        assertFalse(live.publishHookDdlObserved());
        assertFalse(live.midFailAbortHonest());

        Map<String, String> report = G5BrowserFailedPublishHonesty.honestSkipTemplate(live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G5BrowserFailedPublishHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g5_close"));
        assertEquals("false", report.get("g5_94_145_redone"));
        assertEquals("false", report.get("simulate_publish_log_is_fail_click"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G5BrowserFailedPublishHonesty.verify(
                G5BrowserFailedPublishHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeBrowserPass() {
        Map<String, String> fake = new HashMap<>(
                G5BrowserFailedPublishHonesty.parseReport(fixture("fake-browser-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G5BrowserFailedPublishHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("browser_failed_publish"));
    }

    @Test
    void honestyGateRejectsG5CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G5BrowserFailedPublishHonesty.parseReport(fixture("fake-g5-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G5BrowserFailedPublishHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g5_close"));
    }

    @Test
    void honestyGateRejectsPublishLogAsFailClick() {
        Map<String, String> fake = new HashMap<>(
                G5BrowserFailedPublishHonesty.parseReport(fixture("fake-publish-log-click.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G5BrowserFailedPublishHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("simulate_publish_log_is_fail_click"));
    }

    @Test
    void leftoverDocsStayHonestAndKeepBothRows() throws IOException {
        String leftover = readExisting(repoFile("docs/g5-browser-failed-publish.md"));
        String index = readExisting(repoFile("docs/README.md"));
        String claim = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        String mid = readExisting(repoFile("docs/g5-mid-publish-failure.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);

        assertTrue(leftover.contains("SchemaServicePublishHook"));
        assertTrue(leftover.contains("does **not** claim product G5 close")
                || leftover.contains("g5_close=NOT_CLAIMED"));
        assertTrue(leftover.contains("does **not** redo #94") || leftover.contains("Do not redo #94"));
        assertTrue(leftover.contains("#145"));
        assertTrue(leftover.contains("honest SKIP") || leftover.contains("诚实 SKIP"));
        assertTrue(leftover.contains("Keep both leftover rows"));
        assertFalse(lower.contains("product g5 closed"));
        assertFalse(leftover.contains("产品 G5 已关闭"));
        assertFalse(lower.contains("reopen g18"));

        assertTrue(index.contains("[g5-mid-publish-failure](./g5-mid-publish-failure.md)"));
        assertTrue(index.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(index.contains("g9-live-multi-jvm-replica"));
        assertTrue(index.contains("g6-recovery-port-honesty"));
        assertTrue(index.contains("g6-recovery-port-dry-run"));
        assertTrue(index.contains("g2-h5-every-page"));
        assertTrue(index.contains("linux-arm-profile-honesty"));
        assertTrue(index.contains("g10-file-reconcile-honesty"));
        assertTrue(index.contains("g4-export-wizard-deny"));
        assertTrue(index.contains("g15-wx-dt-prod-gap-honesty"));
        assertTrue(index.contains("g3-claim-crash-window"));
        assertTrue(index.contains("g1-datascope-or-in-ast"));
        assertTrue(index.contains("g17-mfa-recovery-email-honesty"));
        assertTrue(index.contains("g9-redis-down-invalidate-honesty"));
        assertTrue(index.contains("g2-form-write-honesty"));
        assertTrue(index.contains("g7-withdraw-browser-honesty"));
        assertFalse(index.toLowerCase(Locale.ROOT).contains("product g5 closed"));

        assertTrue(claim.contains("g5-browser-failed-publish.md"));
        assertTrue(claim.contains("g5-mid-publish-failure.md"));
        assertTrue(claim.contains("g9-live-multi-jvm-replica.md"));
        assertTrue(claim.contains("g2-h5-every-page.md"));
        assertTrue(claim.contains("linux-arm-profile-honesty.md"));
        assertTrue(claim.contains("g10-file-reconcile-honesty.md"));
        assertTrue(claim.contains("g4-export-wizard-deny.md"));
        assertTrue(claim.contains("g15-wx-dt-prod-gap-honesty.md"));
        assertTrue(claim.contains("g3-claim-crash-window.md"));
        assertTrue(claim.contains("g1-datascope-or-in-ast.md"));
        assertTrue(claim.contains("g17-mfa-recovery-email-honesty.md"));
        assertTrue(claim.contains("g9-redis-down-invalidate-honesty.md"));
        assertTrue(claim.contains("g2-form-write-honesty.md"));
        assertTrue(claim.contains("g7-withdraw-browser-honesty.md"));
        assertTrue(claim.contains("不要宣称产品 G5 关闭"));
        assertFalse(claim.contains("产品 G5 已关闭"));

        assertTrue(mid.contains("g5-browser-failed-publish.md"));
        assertTrue(mid.contains("Keep both leftover rows") || mid.contains("browser failed-publish"));
    }

    @Test
    void publishLogViewerIsNotAFailedPublishClick() throws IOException {
        String live = readExisting(repoFile("web/src/api/p3-live.js"));
        String view = readExisting(repoFile("web/src/views/studio/FormStudioView.vue"));
        assertTrue(live.contains("simulatePublishFailure"));
        assertTrue(live.contains("/publish-log"));
        assertTrue(live.contains("UI 不再模拟失败"));
        assertTrue(live.contains("rolled_back: false"));
        assertTrue(view.contains("data-testid=\"form-publish\""));
        assertTrue(view.contains("data-testid=\"form-publish-log\""));
        assertTrue(view.contains("onLog"));
        assertTrue(view.contains("simulatePublishFailure"));
        assertFalse(view.contains("fail publish switch"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g5-browser-failed-publish/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g5-browser-failed-publish/fixtures/" + name);
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
