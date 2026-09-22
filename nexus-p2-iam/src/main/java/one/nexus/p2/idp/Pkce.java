package one.nexus.p2.idp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * RFC 7636 S256 PKCE pair. Used by the opt-in {@code GET /api/v1/auth/authorize-url?pkce=s256}
 * path. Default product authorize URL stays confidential-client without PKCE.
 */
public final class Pkce {
    public static final String METHOD_S256 = "S256";

    public record Challenge(String verifier, String challenge, String method) {
        public Challenge {
            Objects.requireNonNull(verifier, "verifier");
            Objects.requireNonNull(challenge, "challenge");
            if (method == null || method.isBlank()) {
                method = METHOD_S256;
            }
        }
    }

    private Pkce() {}

    public static Challenge s256() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String verifier = encode(raw);
        return s256(verifier);
    }

    public static Challenge s256(String verifier) {
        if (verifier == null || verifier.length() < 43 || verifier.length() > 128) {
            throw new IllegalArgumentException("PKCE code_verifier must be 43-128 characters");
        }
        return new Challenge(verifier, challengeS256(verifier), METHOD_S256);
    }

    static String challengeS256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return encode(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is required for PKCE S256", ex);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
