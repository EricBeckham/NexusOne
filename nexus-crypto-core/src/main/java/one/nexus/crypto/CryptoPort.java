package one.nexus.crypto;

/**
 * Unified crypto interface. Business modules must call this instead of JCE.
 *
 * <p>Productized from {@code poc/p0-10} {@code CryptoContract}: digest, MAC,
 * AEAD round-trip with tamper rejection, and signature round-trip. Ciphertext
 * always includes {@link KeyRef} so key rotation can decrypt older versions.
 */
public interface CryptoPort {
    CryptoSuite suite();

    KeyRef currentKey();

    byte[] digest(byte[] data);

    byte[] mac(byte[] data);

    SealedBytes encrypt(byte[] plaintext);

    byte[] decrypt(SealedBytes sealed);

    default byte[] decrypt(byte[] wire) {
        return decrypt(SealedBytes.decode(wire));
    }

    byte[] sign(byte[] data);

    boolean verify(byte[] data, byte[] signature);

    /** Test/vector hook: encrypt with a caller-supplied nonce. Not for production callers. */
    SealedBytes encryptWithNonce(byte[] plaintext, byte[] nonce);

    /** Rotate AEAD/MAC material; previous versions remain decryptable. */
    KeyRef rotate();
}
