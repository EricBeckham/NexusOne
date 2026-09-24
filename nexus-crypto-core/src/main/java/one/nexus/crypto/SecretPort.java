package one.nexus.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Unified credential access (CORE-SEC-001 / ARCH-007). Business code retrieves
 * credentials only through this port.
 *
 * <p>Implementations include durable JDBC envelope storage
 * ({@link JdbcEncryptedSecretAdapter}), in-process envelope storage for
 * temporary/test use ({@link LocalEncryptedSecretAdapter}), and environment
 * variables ({@link EnvSecretAdapter}). The product default is
 * {@link LocalThenEnvSecretPort} (JDBC ciphertext, then env). OpenBao / cloud
 * KMS / GM KMS adapters are out of S0 scope and must not be required to start
 * the app.
 *
 * <p>Plaintext must not enter Git, ordinary config, or business tables.
 */
public interface SecretPort {
    SecretRef put(String name, byte[] plaintext);

    byte[] get(String name);

    Optional<SecretRef> find(String name);

    void delete(String name);

    CryptoSuite suite();

    /**
     * Deployment handle only; never a secret value. S0 product default is
     * {@code local+env}.
     */
    default String backend() {
        return getClass().getSimpleName();
    }

    default String getUtf8(String name) {
        return new String(get(name), StandardCharsets.UTF_8);
    }
}
