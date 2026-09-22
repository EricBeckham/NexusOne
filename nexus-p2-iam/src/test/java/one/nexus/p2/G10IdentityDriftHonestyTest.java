package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import one.nexus.p2.account.G10IdentityDriftHonesty;
import org.junit.jupiter.api.Test;

/**
 * G10 leftover honesty: live Keycloak 8080 dry-run is honest SKIP when
 * unavailable. Does not redo identity apply / #141 / #169. Does not claim
 * product G10 / SAML / LDAP / SMS MFA.
 */
class G10IdentityDriftHonestyTest {

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        G10IdentityDriftHonesty.Inventory inventory = G10IdentityDriftHonesty.probeLive();
        assertEquals("SKIP", inventory.liveKeycloak8080());
        assertEquals("SKIP", inventory.liveApply());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.listenerPresent() && inventory.oidcDiscovery());
        }

        Map<String, String> report = G10IdentityDriftHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G10IdentityDriftHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g10_close"));
        assertEquals("false", report.get("identity_apply_product_redone"));
        assertEquals("false", report.get("identity_drift_141_redone"));
        assertEquals("false", report.get("file_reconcile_169_redone"));
        assertEquals("false", report.get("saml_claimed"));
        assertEquals("SKIP", report.get("live_apply"));
        assertEquals("false", report.get("live_dry_run_observed"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G10IdentityDriftHonesty.verify(G10IdentityDriftHonesty.parseReport(fixtureFile("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveKeycloakPass() {
        Map<String, String> fake = new HashMap<>(G10IdentityDriftHonesty.parseReport(
                fixtureFile("fake-live-keycloak-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_keycloak"));
    }

    @Test
    void honestyGateRejectsFakeLiveApplyPass() {
        Map<String, String> fake = new HashMap<>(G10IdentityDriftHonesty.parseReport(
                fixtureFile("fake-live-apply-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_apply"));
    }

    @Test
    void honestyGateRejectsG10CloseClaim() {
        Map<String, String> fake = new HashMap<>(G10IdentityDriftHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("g10_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g10_close"));
    }

    @Test
    void honestyGateRejects141169AndApplyRedo() {
        Map<String, String> fake = new HashMap<>(G10IdentityDriftHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("identity_drift_141_redone", "true");
        IllegalArgumentException drift = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(drift.getMessage().contains("identity_drift_141_redone"));

        fake.put("identity_drift_141_redone", "false");
        fake.put("file_reconcile_169_redone", "true");
        IllegalArgumentException file = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(file.getMessage().contains("file_reconcile_169_redone"));

        fake.put("file_reconcile_169_redone", "false");
        fake.put("identity_apply_product_redone", "true");
        IllegalArgumentException apply = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(apply.getMessage().contains("identity_apply_product_redone"));
    }

    @Test
    void honestyGateRejectsSamlLdapSmsClaims() {
        Map<String, String> fake = new HashMap<>(G10IdentityDriftHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("saml_claimed", "true");
        IllegalArgumentException saml = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(saml.getMessage().contains("saml_claimed"));

        fake.put("saml_claimed", "false");
        fake.put("ldap_claimed", "true");
        IllegalArgumentException ldap = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(ldap.getMessage().contains("ldap_claimed"));

        fake.put("ldap_claimed", "false");
        fake.put("sms_mfa_claimed", "true");
        IllegalArgumentException sms = assertThrows(IllegalArgumentException.class,
                () -> G10IdentityDriftHonesty.verify(fake));
        assertTrue(sms.getMessage().contains("sms_mfa_claimed"));
    }

    private static Path fixtureFile(String name) {
        Path first = Path.of("scripts/g10-identity-drift-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        Path second = Path.of("../scripts/g10-identity-drift-honesty/fixtures/" + name);
        if (second.toFile().isFile()) {
            return second;
        }
        return Path.of("../../scripts/g10-identity-drift-honesty/fixtures/" + name);
    }
}
