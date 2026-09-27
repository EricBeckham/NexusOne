package one.nexus.p2.session;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LO2: server-side keeper of the IdP {@code id_token} / {@code refresh_token} from the auth-code exchange, bound to
 * the verified access-token fingerprint, not the account/subject shared by browsers. Tokens never go to the SPA.
 * <p>
 * Process memory only: a restart (or another instance) has no hint and logout degrades to app-only. A hint is
 * single-use — {@link #take} removes it — and expires with the tenant context TTL.
 */
public final class LogoutHintStore {
    /** Token values are deliberately left out of {@link #toString()}. */
    public record Hint(String idToken, String refreshToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "Hint[idToken=" + (idToken == null ? "absent" : "present")
                    + ", refreshToken=" + (refreshToken == null ? "absent" : "present")
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    private final Map<String, Hint> bySession = new ConcurrentHashMap<>();
    private final Clock clock;

    public LogoutHintStore(Clock clock) {
        this.clock = clock;
    }

    /** No raw access token is retained or logged; call this only after validating the token. */
    static String sessionKey(String accessToken) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Keeps only this login's hint; another browser's login must never overwrite it. */
    public void remember(String sessionKey, String idToken, String refreshToken, Instant expiresAt) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        bySession.entrySet().removeIf(entry -> !clock.instant().isBefore(entry.getValue().expiresAt()));
        String id = blankToNull(idToken);
        String refresh = blankToNull(refreshToken);
        if (id == null && refresh == null) {
            bySession.remove(sessionKey);
            return;
        }
        bySession.put(sessionKey, new Hint(id, refresh, expiresAt));
    }

    /** Removes only this session's live hint; expired or missing hints never fall back to subject/account. */
    public Optional<Hint> take(String sessionKey) {
        if (sessionKey == null) {
            return Optional.empty();
        }
        Hint hint = bySession.remove(sessionKey);
        if (hint == null || !clock.instant().isBefore(hint.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(hint);
    }

    public boolean holds(String sessionKey) {
        Hint hint = sessionKey == null ? null : bySession.get(sessionKey);
        return hint != null && clock.instant().isBefore(hint.expiresAt());
    }

    public void forget(String sessionKey) {
        if (sessionKey != null) {
            bySession.remove(sessionKey);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
