package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {
    @Test
    void rfc6238Sha1EightDigitVectors() {
        byte[] seed = Totp.rfc6238Sha1Seed();
        assertEquals("94287082", Totp.generate(seed, 1L, 8, "HmacSHA1"));
        assertEquals("07081804", Totp.generate(seed, 1111111109L / 30, 8, "HmacSHA1"));
        assertEquals("14050471", Totp.generate(seed, 1111111111L / 30, 8, "HmacSHA1"));
    }

    @Test
    void sixDigitTotpFromKnownBase32Secret() {
        // Secret "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" is Base32("12345678901234567890").
        String code = Totp.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Instant.ofEpochSecond(59));
        assertEquals("287082", code);
    }
}
