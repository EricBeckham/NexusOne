package one.nexus.crypto;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.time.Utc;

/**
 * LocalEncryptedSecretAdapter: envelope-encrypts values with {@link CryptoPort}
 * and keeps ciphertext in process memory. S0 product default pairs this overlay
 * with {@link EnvSecretAdapter} via {@link LocalThenEnvSecretPort}. The product
 * does not require OpenBao/KMS. It is not a durable secret store.
 */
public final class LocalEncryptedSecretAdapter implements SecretPort {
    private record Stored(SecretRef ref, byte[] wire) {}

    private final CryptoPort crypto;
    private final ConcurrentHashMap<String, Stored> secrets = new ConcurrentHashMap<>();

    public LocalEncryptedSecretAdapter(CryptoPort crypto) {
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    @Override
    public SecretRef put(String name, byte[] plaintext) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (plaintext == null) {
            throw new NexusException(ErrorCode.SECRET_PLAINTEXT_FORBIDDEN, 400, "secret plaintext is required");
        }
        int nextVersion = secrets.containsKey(name) ? secrets.get(name).ref.secretVersion() + 1 : 1;
        SealedBytes sealed = crypto.encrypt(plaintext);
        Instant now = Utc.now();
        SecretRef ref = new SecretRef(name, nextVersion, sealed.key(), now);
        secrets.put(name, new Stored(ref, sealed.encode()));
        return ref;
    }

    @Override
    public byte[] get(String name) {
        Stored stored = secrets.get(name);
        if (stored == null) {
            throw new NexusException(ErrorCode.SECRET_NOT_FOUND, 404, "secret not found");
        }
        return crypto.decrypt(stored.wire);
    }

    @Override
    public Optional<SecretRef> find(String name) {
        Stored stored = secrets.get(name);
        return stored == null ? Optional.empty() : Optional.of(stored.ref);
    }

    @Override
    public void delete(String name) {
        secrets.remove(name);
    }

    @Override
    public CryptoSuite suite() {
        return crypto.suite();
    }

    @Override
    public String backend() {
        return "local";
    }
}
