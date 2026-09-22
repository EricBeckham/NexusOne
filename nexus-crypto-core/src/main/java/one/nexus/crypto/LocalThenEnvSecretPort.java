package one.nexus.crypto;

import java.util.Objects;
import java.util.Optional;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/**
 * S0 product {@link SecretPort}: in-process overlay first, then environment
 * variables. {@code put}/{@code delete} affect only the local overlay.
 *
 * <p>Env-backed reads are live (no cache). A mutable env map rotates on the
 * next {@code get} when there is no overlay. {@link #system(CryptoPort)}
 * still uses {@code System::getenv} (Java 21 process-start snapshot).
 *
 * <p>Does not introduce OpenBao or KMS. ARCH-007: an independent secret
 * platform is not required to start the app.
 */
public final class LocalThenEnvSecretPort implements SecretPort {
    private final SecretPort local;
    private final SecretPort environment;

    public LocalThenEnvSecretPort(SecretPort local, SecretPort environment) {
        this.local = Objects.requireNonNull(local, "local");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public static LocalThenEnvSecretPort system(CryptoPort crypto) {
        return new LocalThenEnvSecretPort(
                new LocalEncryptedSecretAdapter(crypto), EnvSecretAdapter.system(crypto.suite()));
    }

    @Override
    public SecretRef put(String name, byte[] plaintext) {
        return local.put(name, plaintext);
    }

    @Override
    public byte[] get(String name) {
        if (local.find(name).isPresent()) {
            return local.get(name);
        }
        if (environment.find(name).isPresent()) {
            return environment.get(name);
        }
        throw new NexusException(ErrorCode.SECRET_NOT_FOUND, 404, "secret not found");
    }

    @Override
    public Optional<SecretRef> find(String name) {
        Optional<SecretRef> overlay = local.find(name);
        return overlay.isPresent() ? overlay : environment.find(name);
    }

    @Override
    public void delete(String name) {
        local.delete(name);
    }

    @Override
    public CryptoSuite suite() {
        return local.suite();
    }

    @Override
    public String backend() {
        return "local+env";
    }

    public SecretPort local() {
        return local;
    }

    public SecretPort environment() {
        return environment;
    }
}
