package one.nexus.crypto;

import java.util.Optional;

/**
 * Unified credential access (CORE-SEC-001 / ARCH-007). First-period adapter is
 * local envelope encryption via {@link CryptoPort}. OpenBao / cloud KMS / GM
 * KMS adapters are optional later and must not be required to start the app.
 *
 * <p>Plaintext must not enter Git, ordinary config, or business tables.
 */
public interface SecretPort {
    SecretRef put(String name, byte[] plaintext);

    byte[] get(String name);

    Optional<SecretRef> find(String name);

    void delete(String name);

    CryptoSuite suite();
}
