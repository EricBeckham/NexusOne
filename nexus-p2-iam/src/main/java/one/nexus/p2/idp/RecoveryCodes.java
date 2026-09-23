package one.nexus.p2.idp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One-time admin MFA recovery codes leftover. Matches the Vue leftover
 * ({@code web/src/auth/recoveryCodes.js}). Not a Keycloak write and not a
 * second authenticator stack. Live Keycloak {@code CONFIGURE_RECOVERY_AUTHN_CODES}
 * delivery stays a harness SKIP unless a realm actually issues them.
 */
public final class RecoveryCodes {
    public static final int COUNT = 8;
    public static final int BODY_LEN = 8;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private RecoveryCodes() {}

    public record Issued(List<String> plaintext, List<String> hashes) {}

    public record Consume(boolean ok, String code, String hash, List<String> remainingHashes) {}

    public static Issued issue() {
        return issue(new SecureRandom());
    }

    public static Issued issue(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        List<String> plaintext = new ArrayList<>(COUNT);
        List<String> hashes = new ArrayList<>(COUNT);
        while (plaintext.size() < COUNT) {
            String code = format(randomBody(random));
            String digest = hash(code);
            if (hashes.contains(digest)) {
                continue;
            }
            plaintext.add(code);
            hashes.add(digest);
        }
        return new Issued(List.copyOf(plaintext), List.copyOf(hashes));
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean looksLikeCode(String code) {
        if (code == null) {
            return false;
        }
        String candidate = code.trim().toUpperCase(Locale.ROOT);
        return candidate.matches("(?:[A-Z2-9]{8}|[A-Z2-9]{4}-[A-Z2-9]{4})");
    }

    public static String format(String body) {
        String compact = normalize(body);
        if (compact.length() != BODY_LEN) {
            throw new IllegalArgumentException("recovery code body must be " + BODY_LEN + " chars");
        }
        return compact.substring(0, 4) + "-" + compact.substring(4);
    }

    public static String hash(String code) {
        String compact = normalize(code);
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("recovery code is blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(compact.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static Consume consume(List<String> hashes, String code) {
        if (!looksLikeCode(code)) {
            return new Consume(false, normalize(code), null, hashes == null ? List.of() : List.copyOf(hashes));
        }
        String digest = hash(code);
        List<String> remaining = new ArrayList<>();
        boolean matched = false;
        if (hashes != null) {
            for (String item : hashes) {
                if (!matched && digest.equalsIgnoreCase(item)) {
                    matched = true;
                    continue;
                }
                remaining.add(item);
            }
        }
        return new Consume(matched, format(code), digest, List.copyOf(remaining));
    }

    private static String randomBody(SecureRandom random) {
        char[] body = new char[BODY_LEN];
        for (int i = 0; i < BODY_LEN; i++) {
            body[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(body);
    }
}
