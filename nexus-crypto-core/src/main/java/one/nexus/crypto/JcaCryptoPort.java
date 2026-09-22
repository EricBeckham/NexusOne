package one.nexus.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/**
 * JCA implementation of {@link CryptoPort}. International algorithms use the
 * JDK provider; SM2/SM3/SM4 use Bouncy Castle {@code BC} as in {@code poc/p0-10}.
 */
public final class JcaCryptoPort implements CryptoPort {
    private final Keyring keyring;

    public JcaCryptoPort(Keyring keyring) {
        this.keyring = Objects.requireNonNull(keyring, "keyring");
    }

    public static JcaCryptoPort ephemeral(CryptoSuite suite) {
        return new JcaCryptoPort(Keyring.ephemeral(suite, "local"));
    }

    public static JcaCryptoPort fromMasterKey(CryptoSuite suite, String keyId, int version, byte[] masterKey) {
        return new JcaCryptoPort(Keyring.fromMasterKey(suite, keyId, version, masterKey));
    }

    @Override
    public CryptoSuite suite() {
        return keyring.suite();
    }

    @Override
    public KeyRef currentKey() {
        return keyring.current();
    }

    @Override
    public byte[] digest(byte[] data) {
        try {
            return Providers.messageDigest(suite().digestAlgorithm(), suite().provider()).digest(require(data));
        } catch (GeneralSecurityException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public byte[] mac(byte[] data) {
        try {
            Mac mac = Providers.mac(suite().macAlgorithm(), suite().provider());
            mac.init(keyring.material(currentKey()).macKey());
            return mac.doFinal(require(data));
        } catch (GeneralSecurityException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public SealedBytes encrypt(byte[] plaintext) {
        byte[] nonce = new byte[SealedBytes.NONCE_LENGTH];
        Providers.RANDOM.nextBytes(nonce);
        return encryptWithNonce(plaintext, nonce);
    }

    @Override
    public SealedBytes encryptWithNonce(byte[] plaintext, byte[] nonce) {
        KeyRef ref = currentKey();
        try {
            Cipher cipher = Providers.cipher(suite().cipherAlgorithm(), suite().provider());
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyring.material(ref).aeadKey(),
                    new GCMParameterSpec(SealedBytes.TAG_BITS, nonce));
            cipher.updateAAD(ref.aad().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = cipher.doFinal(require(plaintext));
            return new SealedBytes(ref, nonce, payload);
        } catch (GeneralSecurityException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public byte[] decrypt(SealedBytes sealed) {
        Objects.requireNonNull(sealed, "sealed");
        if (sealed.key().suite() != suite()) {
            throw new NexusException(
                    ErrorCode.CRYPTO_SUITE_MISMATCH, 400, "ciphertext suite does not match deployment suite");
        }
        CryptoKeys keys = keyring.material(sealed.key());
        try {
            Cipher cipher = Providers.cipher(suite().cipherAlgorithm(), suite().provider());
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keys.aeadKey(),
                    new GCMParameterSpec(SealedBytes.TAG_BITS, sealed.nonce()));
            cipher.updateAAD(sealed.key().aad().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cipher.doFinal(sealed.ciphertextAndTag());
        } catch (GeneralSecurityException ex) {
            throw new NexusException(ErrorCode.CRYPTO_INTEGRITY, 400, "ciphertext failed integrity check", java.util.Map.of());
        }
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            var signature = Providers.signature(suite().signatureAlgorithm(), suite().provider());
            signature.initSign(keyring.material(currentKey()).signingPrivate(), Providers.RANDOM);
            signature.update(require(data));
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] proof) {
        try {
            var signature = Providers.signature(suite().signatureAlgorithm(), suite().provider());
            signature.initVerify(keyring.material(currentKey()).signingPublic());
            signature.update(require(data));
            return signature.verify(proof);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    @Override
    public KeyRef rotate() {
        return keyring.rotate();
    }

    private static byte[] require(byte[] data) {
        return Objects.requireNonNull(data, "data");
    }

    private static RuntimeException wrap(GeneralSecurityException ex) {
        return new IllegalStateException("JCA failure on CryptoPort", ex);
    }

    /** Visible for vector tests that need a copy of ciphertext bytes. */
    static byte[] xorLast(byte[] input) {
        byte[] copy = Arrays.copyOf(input, input.length);
        copy[copy.length - 1] ^= 1;
        return copy;
    }
}
