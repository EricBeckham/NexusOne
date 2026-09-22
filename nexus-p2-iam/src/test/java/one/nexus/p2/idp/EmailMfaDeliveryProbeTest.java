package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailMfaDeliveryProbeTest {
    @Test
    void smtpDisabledIsHonestSkip() {
        EmailMfaDeliveryProbe.Result result = EmailMfaDeliveryProbe.probe(false, "", "");
        assertEquals("SKIP", result.emailDelivery());
        assertEquals("SKIP", result.recoveryDelivery());
        assertFalse(result.smtpEnabled());
        assertFalse(result.smtpHostConfigured());
        assertTrue(result.skipReason().contains(EmailMfaDeliveryProbe.SMTP_DISABLED));
        assertFalse(result.skipReason().toLowerCase().contains("pass"));
    }

    @Test
    void enabledWithoutHostStillSkips() {
        EmailMfaDeliveryProbe.Result result = EmailMfaDeliveryProbe.probe(true, "", "");
        assertEquals("SKIP", result.emailDelivery());
        assertTrue(result.skipReason().contains(EmailMfaDeliveryProbe.EMAIL_OTP_NOT_DELIVERED));
        assertFalse(result.smtpHostConfigured());
    }

    @Test
    void enabledHostWithoutKeycloakStillSkips() {
        EmailMfaDeliveryProbe.Result result = EmailMfaDeliveryProbe.probe(
                true, "http://127.0.0.1:9/realms/nexus", "127.0.0.1");
        assertEquals("SKIP", result.emailDelivery());
        assertEquals("SKIP", result.recoveryDelivery());
        assertTrue(result.smtpHostConfigured());
        assertFalse(result.keycloakReachable());
        assertTrue(result.skipReason().contains(EmailMfaDeliveryProbe.EMAIL_OTP_NOT_DELIVERED));
    }
}
