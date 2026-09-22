package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MfaClaimsTest {
    @Test
    void passwordOnlyAcrIsNotMfa() {
        assertFalse(MfaClaims.satisfied(null, null));
        assertFalse(MfaClaims.satisfied("1", "pwd"));
        assertFalse(MfaClaims.satisfied("0", null));
    }

    @Test
    void acrOrAmrMarksSessionAsSteppedUp() {
        assertTrue(MfaClaims.satisfied("mfa", null));
        assertTrue(MfaClaims.satisfied("2", null));
        assertTrue(MfaClaims.satisfied("aal2", "pwd"));
        assertTrue(MfaClaims.satisfied(null, "pwd otp"));
        assertTrue(MfaClaims.satisfied(null, "[\"otp\"]"));
        assertTrue(MfaClaims.satisfied(null, "email"));
        assertTrue(MfaClaims.satisfied(null, "mca"));
        assertTrue(MfaClaims.satisfied(null, "recovery-authn-code"));
        assertFalse(MfaClaims.satisfied("1", "pwd"));
    }
}
