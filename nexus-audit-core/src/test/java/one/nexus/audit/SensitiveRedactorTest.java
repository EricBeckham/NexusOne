package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveRedactorTest {
    @Test
    void redactsTokenLikeKeys() {
        Map<String, String> redacted = SensitiveRedactor.redact(Map.of(
                "access_token", "abc",
                "clientSecret", "shh",
                "user", "alice"));
        assertEquals(SensitiveRedactor.REDACTED, redacted.get("access_token"));
        assertEquals(SensitiveRedactor.REDACTED, redacted.get("clientSecret"));
        assertEquals("alice", redacted.get("user"));
        assertTrue(SensitiveRedactor.isSensitive("NEXUS_CRYPTO_MASTER_KEY"));
    }
}
