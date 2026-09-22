package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PkceTest {
    @Test
    void s256VerifierIsUnreservedAndChallengeMatchesRfc7636() {
        Pkce.Challenge pair = Pkce.s256();
        assertEquals(Pkce.METHOD_S256, pair.method());
        assertTrue(pair.verifier().length() >= 43 && pair.verifier().length() <= 128, pair.verifier());
        assertTrue(pair.verifier().matches("[A-Za-z0-9_-]+"), pair.verifier());
        assertEquals(Pkce.challengeS256(pair.verifier()), pair.challenge());
        assertNotEquals(pair.verifier(), Pkce.s256().verifier());
    }

    @Test
    void rejectsShortVerifier() {
        assertThrows(IllegalArgumentException.class, () -> Pkce.s256("too-short"));
    }
}
