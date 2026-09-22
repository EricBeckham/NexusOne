package one.nexus.crypto;

import java.util.Objects;

/**
 * Versioned key identity carried on every sealed blob so rotation can decrypt
 * historical ciphertext. {@code keyId} is a deployment handle, not a secret.
 */
public record KeyRef(CryptoSuite suite, String keyId, int version) {
    public KeyRef {
        Objects.requireNonNull(suite, "suite");
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    public String aad() {
        return suite.name() + ":" + keyId + ":v" + version;
    }
}
