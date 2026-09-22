package one.nexus.crypto;

import java.time.Instant;
import java.util.Objects;

/**
 * Handle returned by {@link SecretPort}. Payload is never the plaintext.
 */
public record SecretRef(String name, int secretVersion, KeyRef cryptoKey, Instant updatedAt) {
    public SecretRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (secretVersion < 1) {
            throw new IllegalArgumentException("secretVersion must be >= 1");
        }
        Objects.requireNonNull(cryptoKey, "cryptoKey");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
