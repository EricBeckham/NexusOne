package one.nexus.p2.idp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (HmacSHA1, 6 digits, 30s). Used to drive Keycloak's
 * enrollment / OTP forms. Not a second authenticator stack.
 */
public final class Totp {
    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;

    private Totp() {}

    public static String generate(String base32Secret) {
        return generate(base32Secret, Instant.now());
    }

    public static String generate(String base32Secret, Clock clock) {
        return generate(base32Secret, clock.instant());
    }

    public static String generate(String base32Secret, Instant when) {
        return generate(decodeBase32(base32Secret), when.getEpochSecond() / PERIOD_SECONDS, DIGITS, "HmacSHA1");
    }

    static String generate(byte[] key, long counter, int digits, String algorithm) {
        try {
            byte[] payload = new byte[8];
            long value = counter;
            for (int i = 7; i >= 0; i--) {
                payload[i] = (byte) (value & 0xff);
                value >>= 8;
            }
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            byte[] hash = mac.doFinal(payload);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % pow10(digits);
            String text = Integer.toString(otp);
            if (text.length() >= digits) {
                return text.substring(text.length() - digits);
            }
            return "0".repeat(digits - text.length()) + text;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP generation failed", ex);
        }
    }

    static byte[] decodeBase32(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("TOTP secret is blank");
        }
        String compact = raw.trim().replace(" ", "").replace("-", "").toUpperCase();
        int pad = (8 - (compact.length() % 8)) % 8;
        compact = compact + "=".repeat(pad);
        int[] map = new int[128];
        java.util.Arrays.fill(map, -1);
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        for (int i = 0; i < alphabet.length; i++) {
            map[alphabet[i]] = i;
        }
        int buffer = 0;
        int bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < compact.length(); i++) {
            char ch = compact.charAt(i);
            if (ch == '=') {
                break;
            }
            if (ch >= map.length || map[ch] < 0) {
                throw new IllegalArgumentException("invalid Base32 TOTP secret");
            }
            buffer = (buffer << 5) | map[ch];
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }

    /** RFC 6238 Appendix B SHA-1 seed ({@code 12345678901234567890}). */
    static byte[] rfc6238Sha1Seed() {
        return "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    }

    private static int pow10(int digits) {
        int value = 1;
        for (int i = 0; i < digits; i++) {
            value *= 10;
        }
        return value;
    }
}
