package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * G17 leftover honesty beyond #154: local recovery / email request stay
 * always-green; live SMTP / Keycloak recovery-authn-codes is honest SKIP
 * when unavailable. Does not redo #96/#118/#133/#154/#158/#160.
 */
class G17MfaRecoveryEmailHonestyTest {

    @Test
    void localRecoveryCodesStillIssueAndConsumeOnce() {
        RecoveryCodes.Issued issued = RecoveryCodes.issue(new SecureRandom(new byte[] {1, 5, 4}));
        assertEquals(RecoveryCodes.COUNT, issued.plaintext().size());
        String code = issued.plaintext().getFirst();
        RecoveryCodes.Consume first = RecoveryCodes.consume(issued.hashes(), code);
        assertTrue(first.ok());
        RecoveryCodes.Consume reuse = RecoveryCodes.consume(first.remainingHashes(), code);
        assertFalse(reuse.ok());
    }

    @Test
    void emailDeliveryProbeStillSkipsWithoutSmtp() {
        EmailMfaDeliveryProbe.Result result = EmailMfaDeliveryProbe.probe(false, "", "");
        assertEquals("SKIP", result.emailDelivery());
        assertEquals("SKIP", result.recoveryDelivery());
        assertTrue(result.skipReason().contains(EmailMfaDeliveryProbe.SMTP_DISABLED));
    }

    @Test
    void honestSkipTemplatePassesTheGate() {
        G17MfaRecoveryEmailHonesty.Inventory inventory = G17MfaRecoveryEmailHonesty.probeLive();
        Map<String, String> report = G17MfaRecoveryEmailHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G17MfaRecoveryEmailHonesty.verify(report);
        assertEquals("SKIP", report.get("email_delivery"));
        assertEquals("SKIP", report.get("recovery_live"));
        assertEquals(G17MfaRecoveryEmailHonesty.G17_CLOSE, report.get("g17_close"));
        assertEquals("false", report.get("mailbox_observed"));
        assertEquals("false", report.get("redo_154"));
    }

    @Test
    void fakeDeliveryPassWithoutMailboxIsRejected() {
        Map<String, String> report = G17MfaRecoveryEmailHonesty.honestSkipTemplate(
                new G17MfaRecoveryEmailHonesty.Inventory(
                        false, false, false, "", EmailMfaDeliveryProbe.SMTP_DISABLED,
                        "keycloak_issuer_unreachable"));
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        report.put("email_delivery", "PASS");
        report.put("mailbox_observed", "true");
        report.put("email_delivery_skip_reason", "");
        List<String> errors = G17MfaRecoveryEmailHonesty.verifyErrors(report);
        assertTrue(errors.stream().anyMatch(item -> item.contains("smtp_enabled")));
        assertThrows(IllegalArgumentException.class, () -> G17MfaRecoveryEmailHonesty.verify(report));
    }

    @Test
    void fakeG17CloseIsRejected() {
        Map<String, String> report = new HashMap<>(G17MfaRecoveryEmailHonesty.honestSkipTemplate(
                new G17MfaRecoveryEmailHonesty.Inventory(
                        false, false, false, "", EmailMfaDeliveryProbe.SMTP_DISABLED,
                        "keycloak_issuer_unreachable")));
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        report.put("g17_close", "CLOSED");
        report.put("dengbao_claimed", "true");
        List<String> errors = G17MfaRecoveryEmailHonesty.verifyErrors(report);
        assertTrue(errors.stream().anyMatch(item -> item.contains("g17_close")));
    }

    @Test
    void fixtureHonestSkipIsAccepted() {
        Path fixture = Path.of("scripts/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env");
        if (!fixture.toFile().isFile()) {
            fixture = Path.of("../scripts/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env");
        }
        if (!fixture.toFile().isFile()) {
            fixture = Path.of("../../scripts/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env");
        }
        Map<String, String> report = G17MfaRecoveryEmailHonesty.parseReport(fixture);
        G17MfaRecoveryEmailHonesty.verify(report);
        assertEquals("SKIP", report.get("email_delivery"));
        assertEquals("NOT_CLAIMED", report.get("g17_close"));
    }
}
