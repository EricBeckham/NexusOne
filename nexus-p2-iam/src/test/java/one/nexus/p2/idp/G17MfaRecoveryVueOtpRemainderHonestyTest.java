package one.nexus.p2.idp;

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
import one.nexus.p2.idp.G17MfaRecoveryVueOtpRemainderHonesty.LiveInventory;
import one.nexus.p2.idp.G17MfaRecoveryVueOtpRemainderHonesty.ParentHarness;
import org.junit.jupiter.api.Test;

/**
 * G17 leftover remainder beyond #205 / #172 / #158: parent leftover harness
 * stays always-green; live IdP / SMTP delivery / Vue otp write-back is honest
 * SKIP when unavailable. Does not redo #96/#118/#133/#154/#158/#172/#205.
 * Does not claim product G17 / 等保 / 密评.
 */
class G17MfaRecoveryVueOtpRemainderHonestyTest {

    @Test
    void parentHarnessStillPassesWithoutRedoing205Or172Or158() {
        ParentHarness parent = G17MfaRecoveryVueOtpRemainderHonesty.proveParentHarness();
        assertEquals("PASS", parent.leftover205());
        assertEquals("PASS", parent.leftover172());
        assertEquals("PASS", parent.leftover158());
        assertEquals("PASS", parent.leftover205Cells());
        assertEquals("PASS", parent.leftover172Cells());
        assertEquals("PASS", parent.leftover158Cells());
    }

    @Test
    void liveIdpSmtpIsHonestSkipWhenUnavailable() {
        LiveInventory live = G17MfaRecoveryVueOtpRemainderHonesty.probeLive();
        assertEquals("SKIP", live.emailDelivery());
        assertEquals("SKIP", live.recoveryLive());
        assertEquals("SKIP", live.vueOtpWriteback());
        assertFalse(live.emailSkipReason().isBlank());
        assertFalse(live.recoverySkipReason().isBlank());
        assertFalse(live.writebackSkipReason().isBlank());
        assertTrue(live.emailSkipReason().contains("SMTP")
                || live.emailSkipReason().contains("mailbox")
                || live.emailSkipReason().contains("keycloak")
                || live.emailSkipReason().contains("EMAIL_OTP"));
        assertTrue(live.recoverySkipReason().contains("keycloak")
                || live.recoverySkipReason().contains("recovery"));
        assertTrue(live.writebackSkipReason().contains("keycloak")
                || live.writebackSkipReason().contains("writeback")
                || live.writebackSkipReason().contains("endpoint"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        ParentHarness parent = G17MfaRecoveryVueOtpRemainderHonesty.proveParentHarness();
        LiveInventory live = G17MfaRecoveryVueOtpRemainderHonesty.probeLive();
        Map<String, String> report = G17MfaRecoveryVueOtpRemainderHonesty.honestSkipTemplate(parent, live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G17MfaRecoveryVueOtpRemainderHonesty.verify(report);
        assertEquals("PASS", report.get("leftover_205_harness"));
        assertEquals("PASS", report.get("leftover_172_harness"));
        assertEquals("PASS", report.get("leftover_158_harness"));
        assertEquals("SKIP", report.get("email_delivery"));
        assertEquals("SKIP", report.get("recovery_live"));
        assertEquals("SKIP", report.get("vue_otp_writeback"));
        assertEquals(G17MfaRecoveryVueOtpRemainderHonesty.G17_CLOSE, report.get("g17_close"));
        assertEquals("false", report.get("redo_205"));
        assertEquals("false", report.get("redo_172"));
        assertEquals("false", report.get("redo_158"));
        assertEquals("false", report.get("dengbao_claimed"));
        assertEquals("false", report.get("mipin_claimed"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertTrue(report.get("leftover_rows").contains("g17-mfa-recovery-vue-otp-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-mfa-recovery-email-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-vue-otp-writeback"));
        assertTrue(report.get("leftover_rows").contains("s0-secret-rotate-getenv-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g10-leftover-honesty-remainder"));
        assertTrue(report.get("leftover_rows").contains("g2-report-physical-dump-honesty"));
        assertTrue(report.get("leftover_rows").contains("g3-outbox-consume-idempotent-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-live-multi-jvm-replica-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g4-hot-purge-staging-leftover-honesty"));
        assertTrue(report.get("leftover_rows").contains("g7-withdraw-browser-evidence-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-h5-every-page-form-write-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g7-query-expense-travel-honesty-remainder"));
        assertTrue(report.get("leftover_rows").contains("g1-fieldpolicy-mask-second-auth-remainder-honesty"));
        G17MfaRecoveryVueOtpRemainderHonesty.verify(
                G17MfaRecoveryVueOtpRemainderHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeDeliveryPass() {
        Map<String, String> fake = new HashMap<>(
                G17MfaRecoveryVueOtpRemainderHonesty.parseReport(fixture("fake-delivery-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G17MfaRecoveryVueOtpRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("email_delivery")
                || ex.getMessage().contains("smtp_enabled")
                || ex.getMessage().contains("recovery_live"));
    }

    @Test
    void honestyGateRejectsFakeWritebackPass() {
        Map<String, String> fake = new HashMap<>(
                G17MfaRecoveryVueOtpRemainderHonesty.parseReport(fixture("fake-writeback-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G17MfaRecoveryVueOtpRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("vue_otp_writeback"));
    }

    @Test
    void honestyGateRejectsG17CloseAndRedo() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G17MfaRecoveryVueOtpRemainderHonesty.verify(
                        G17MfaRecoveryVueOtpRemainderHonesty.parseReport(fixture("fake-g17-close.env"))));
        assertTrue(close.getMessage().contains("g17_close")
                || close.getMessage().contains("dengbao")
                || close.getMessage().contains("mipin"));

        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G17MfaRecoveryVueOtpRemainderHonesty.verify(
                        G17MfaRecoveryVueOtpRemainderHonesty.parseReport(fixture("fake-redone.env"))));
        assertTrue(redo.getMessage().contains("redo_205")
                || redo.getMessage().contains("redo_172")
                || redo.getMessage().contains("redo_158"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndRefuseG17Close() throws IOException {
        String leftover = readExisting(repoFile("docs/g17-mfa-recovery-vue-otp-remainder-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#205"));
        assertTrue(leftover.contains("#172"));
        assertTrue(leftover.contains("#158"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("g17-mfa-recovery-vue-otp-honesty"));
        assertTrue(leftover.contains("g17-mfa-recovery-email-honesty"));
        assertTrue(leftover.contains("g17-vue-otp-writeback"));
        assertTrue(leftover.contains("s0-secret-rotate-getenv-remainder-honesty"));
        assertTrue(leftover.contains("g6-recovery-port-dry-run-remainder-honesty"));
        assertTrue(leftover.contains("g10-leftover-honesty-remainder"));
        assertTrue(leftover.contains("g2-report-physical-dump-honesty"));
        assertTrue(leftover.contains("g3-outbox-consume-idempotent-honesty"));
        assertTrue(leftover.contains("g9-live-multi-jvm-replica-remainder-honesty"));
        assertTrue(leftover.contains("g4-hot-purge-staging-leftover-honesty"));
        assertTrue(leftover.contains("g7-withdraw-browser-evidence-honesty"));
        assertTrue(leftover.contains("g2-h5-every-page-form-write-remainder-honesty"));
        assertTrue(leftover.contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(leftover.contains("g7-query-expense-travel-honesty-remainder"));
        assertTrue(leftover.contains("g1-fieldpolicy-mask-second-auth-remainder-honesty"));
        assertTrue(leftover.contains("g4-pkce-spa-verifier-remainder-honesty"));
        assertTrue(leftover.contains("g17-separation-leftover-honesty"));
        assertTrue(leftover.contains("does **not** redo #205") || leftover.contains("Does **not** redo #205")
                || leftover.contains("不重做 #205"));
        assertTrue(leftover.contains("does **not** redo #172") || leftover.contains("Does **not** redo #172")
                || leftover.contains("不重做 #172"));
        assertTrue(leftover.contains("does **not** redo #158") || leftover.contains("Does **not** redo #158")
                || leftover.contains("不重做 #158"));
        assertFalse(leftover.contains("产品 G17 已关闭"));
        assertFalse(leftover.contains("等保已关闭"));
        assertFalse(leftover.contains("密评已关闭"));
        assertFalse(lower.contains("product g17 is closed"));
        assertFalse(lower.contains("dengbao closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : G17MfaRecoveryVueOtpRemainderHonesty.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g17 closed"));
        assertFalse(readme.contains("产品 G17 已关闭"));
        assertFalse(readme.contains("等保已关闭"));
        assertFalse(readme.contains("密评已关闭"));
    }

    @Test
    void claimListKeepsG17MfaLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g17-mfa-recovery-vue-otp-honesty.md"));
        assertTrue(text.contains("g17-mfa-recovery-email-honesty.md"));
        assertTrue(text.contains("g17-vue-otp-writeback.md"));
        assertTrue(text.contains("g17-mfa-recovery-vue-otp-remainder-honesty.md"));
        assertTrue(text.contains("#205"));
        assertTrue(text.contains("#172"));
        assertTrue(text.contains("#158"));
        assertTrue(text.contains("不宣称完整 G17") || text.contains("不宣称等保") || text.contains("不宣称产品 G17"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G17 已关闭"));
        assertFalse(text.contains("完整 G17 已关闭"));
        assertFalse(text.contains("等保已关闭"));
        assertFalse(text.contains("密评已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/" + name);
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
