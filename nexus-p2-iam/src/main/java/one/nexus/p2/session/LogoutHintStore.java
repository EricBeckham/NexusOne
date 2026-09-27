package one.nexus.p2.session;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LO2: server-side keeper of the IdP {@code id_token} / {@code refresh_token} from the auth-code exchange, bound to
 * the IdP subject, so logout can end the Keycloak session. These tokens never go to the SPA.
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

    private final Map<String, Hint> bySubject = new ConcurrentHashMap<>();
    private final Clock clock;

    public LogoutHintStore(Clock clock) {
        this.clock = clock;
    }

    /** Keeps the latest hint for {@code subject}; blank tokens are dropped, and nothing is kept if both are blank. */
    public void remember(String subject, String idToken, String refreshToken, Instant expiresAt) {
        if (subject == null || subject.isBlank()) {
            return;
        }
        String id = blankToNull(idToken);
        String refresh = blankToNull(refreshToken);
        if (id == null && refresh == null) {
            bySubject.remove(subject);
            return;
        }
        bySubject.put(subject, new Hint(id, refresh, expiresAt));
    }

    /** Removes and returns the live hint for {@code subject}; an expired hint is discarded and reported as absent. */
    public Optional<Hint> take(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        Hint hint = bySubject.remove(subject);
        if (hint == null || !clock.instant().isBefore(hint.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(hint);
    }

    public boolean holds(String subject) {
        Hint hint = subject == null ? null : bySubject.get(subject);
        return hint != null && clock.instant().isBefore(hint.expiresAt());
    }

    public void forget(String subject) {
        if (subject != null) {
            bySubject.remove(subject);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
