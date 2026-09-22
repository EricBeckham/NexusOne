package one.nexus.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/**
 * Versioned key material for one {@link CryptoSuite}. Signing keys are
 * process-local until a KMS/HSM adapter is wired through {@link SecretPort}.
 */
public final class Keyring {
    private final CryptoSuite suite;
    private final String keyId;
    private final SecureRandom random;
    private final Map<Integer, CryptoKeys> versions = new ConcurrentHashMap<>();
    private final AtomicInteger currentVersion;

    private Keyring(CryptoSuite suite, String keyId, int startVersion, SecureRandom random) {
        this.suite = Objects.requireNonNull(suite, "suite");
        this.keyId = keyId;
        this.random = random;
        this.currentVersion = new AtomicInteger(startVersion);
    }

    public static Keyring ephemeral(CryptoSuite suite, String keyId) {
        return ephemeral(suite, keyId, 1, new SecureRandom());
    }

    public static Keyring ephemeral(CryptoSuite suite, String keyId, int version, SecureRandom random) {
        Keyring ring = new Keyring(suite, keyId, version, random);
        ring.versions.put(version, ring.generate(version));
        return ring;
    }

    /**
     * Derive AEAD + MAC keys from a deployment master key. Master key never
     * enters the business database. Signing keys remain process-ephemeral.
     */
    public static Keyring fromMasterKey(CryptoSuite suite, String keyId, int version, byte[] masterKey) {
        if (masterKey == null || masterKey.length < suite.keyLengthBytes()) {
            throw new NexusException(
                    ErrorCode.CRYPTO_KEY_UNKNOWN,
                    500,
                    "master key must be at least " + suite.keyLengthBytes() + " bytes for " + suite);
        }
        Keyring ring = new Keyring(suite, keyId, version, new SecureRandom());
        ring.versions.put(version, ring.derive(version, masterKey));
        return ring;
    }

    public CryptoSuite suite() {
        return suite;
    }

    public KeyRef current() {
        int version = currentVersion.get();
        return versions.get(version).ref();
    }

    public CryptoKeys material(KeyRef ref) {
        if (ref.suite() != suite) {
            throw new NexusException(ErrorCode.CRYPTO_SUITE_MISMATCH, 400, "ciphertext suite does not match deployment");
        }
        if (!keyId.equals(ref.keyId())) {
            throw new NexusException(ErrorCode.CRYPTO_KEY_UNKNOWN, 400, "unknown key id");
        }
        CryptoKeys keys = versions.get(ref.version());
        if (keys == null) {
            throw new NexusException(ErrorCode.CRYPTO_KEY_UNKNOWN, 400, "unknown key version " + ref.version());
        }
        return keys;
    }

    public synchronized KeyRef rotate() {
        int next = currentVersion.incrementAndGet();
        versions.put(next, generate(next));
        return versions.get(next).ref();
    }

    private CryptoKeys generate(int version) {
        try {
            KeyGenerator kg = Providers.keyGenerator(suite.keyAlgorithm(), suite.provider());
            kg.init(suite.keyBits(), random);
            SecretKey aead = kg.generateKey();
            KeyGenerator macGen = Providers.keyGenerator(suite.macAlgorithm(), suite.provider());
            SecretKey mac = macGen.generateKey();
            KeyPair signing = signingPair();
            return new CryptoKeys(new KeyRef(suite, keyId, version), aead, mac, signing.getPrivate(), signing.getPublic());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("unable to generate " + suite + " keys", ex);
        }
    }

    private CryptoKeys derive(int version, byte[] masterKey) {
        try {
            byte[] aeadBytes = expand(masterKey, "nexusone.aead." + version, suite.keyLengthBytes());
            byte[] macBytes = expand(masterKey, "nexusone.mac." + version, 32);
            SecretKey aead = new SecretKeySpec(aeadBytes, suite.keyAlgorithm());
            SecretKey mac = new SecretKeySpec(macBytes, suite.macAlgorithm());
            KeyPair signing = signingPair();
            return new CryptoKeys(new KeyRef(suite, keyId, version), aead, mac, signing.getPrivate(), signing.getPublic());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("unable to derive " + suite + " keys", ex);
        }
    }

    private byte[] expand(byte[] master, String label, int length) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(master, "HmacSHA256"));
        byte[] digest = hmac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        if (digest.length < length) {
            throw new IllegalStateException("HKDF-lite output too short");
        }
        return java.util.Arrays.copyOf(digest, length);
    }

    private KeyPair signingPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = Providers.keyPairGenerator("EC", suite.provider());
        kpg.initialize(new ECGenParameterSpec(suite.curve()), random);
        return kpg.generateKeyPair();
    }

    static byte[] digest(byte[] data, String algorithm, String provider) throws GeneralSecurityException {
        MessageDigest md = Providers.messageDigest(algorithm, provider);
        return md.digest(data);
    }
}
