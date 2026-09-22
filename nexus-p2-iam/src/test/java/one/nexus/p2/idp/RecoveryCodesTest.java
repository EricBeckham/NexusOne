package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryCodesTest {
    @Test
    void issuesEightDistinctHashedCodes() {
        RecoveryCodes.Issued issued = RecoveryCodes.issue(new SecureRandom(new byte[] {7, 17, 23}));
        assertEquals(RecoveryCodes.COUNT, issued.plaintext().size());
        assertEquals(RecoveryCodes.COUNT, issued.hashes().size());
        assertEquals(RecoveryCodes.COUNT, issued.plaintext().stream().distinct().count());
        for (String code : issued.plaintext()) {
            assertTrue(RecoveryCodes.looksLikeCode(code), code);
            assertEquals(RecoveryCodes.hash(code), issued.hashes().get(issued.plaintext().indexOf(code)));
        }
    }

    @Test
    void consumesOnceAndRejectsReuseBlankAndFormat() {
        String code = "AB2D-EF3G";
        String digest = RecoveryCodes.hash(code);
        assertEquals(digest, RecoveryCodes.hash("ab2d ef3g"));
        RecoveryCodes.Consume first = RecoveryCodes.consume(List.of(digest), "ab2d-ef3g");
        assertTrue(first.ok());
        assertTrue(first.remainingHashes().isEmpty());
        RecoveryCodes.Consume reuse = RecoveryCodes.consume(first.remainingHashes(), code);
        assertFalse(reuse.ok());
        assertFalse(RecoveryCodes.consume(List.of(digest), "").ok());
        assertFalse(RecoveryCodes.consume(List.of(digest), "12").ok());
        assertFalse(RecoveryCodes.looksLikeCode("not-a-code"));
    }

    @Test
    void hashIsNotThePlaintext() {
        String code = "K7NP-4W2M";
        assertNotEquals(code, RecoveryCodes.hash(code));
        assertEquals(64, RecoveryCodes.hash(code).length());
    }
}
