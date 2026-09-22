package one.nexus.crypto;

/**
 * Deployment-level algorithm suite. Ciphertext must not mix suites.
 */
public enum CryptoSuite {
    INTERNATIONAL("SHA-256", "HmacSHA256", "AES/GCM/NoPadding", "AES", 256, "SHA256withECDSA", "secp256r1", null),
    GM("SM3", "HmacSM3", "SM4/GCM/NoPadding", "SM4", 128, "SM3withSM2", "sm2p256v1", "BC");

    private final String digestAlgorithm;
    private final String macAlgorithm;
    private final String cipherAlgorithm;
    private final String keyAlgorithm;
    private final int keyBits;
    private final String signatureAlgorithm;
    private final String curve;
    private final String provider;

    CryptoSuite(
            String digestAlgorithm,
            String macAlgorithm,
            String cipherAlgorithm,
            String keyAlgorithm,
            int keyBits,
            String signatureAlgorithm,
            String curve,
            String provider) {
        this.digestAlgorithm = digestAlgorithm;
        this.macAlgorithm = macAlgorithm;
        this.cipherAlgorithm = cipherAlgorithm;
        this.keyAlgorithm = keyAlgorithm;
        this.keyBits = keyBits;
        this.signatureAlgorithm = signatureAlgorithm;
        this.curve = curve;
        this.provider = provider;
    }

    public String digestAlgorithm() {
        return digestAlgorithm;
    }

    public String macAlgorithm() {
        return macAlgorithm;
    }

    public String cipherAlgorithm() {
        return cipherAlgorithm;
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    public int keyBits() {
        return keyBits;
    }

    public int keyLengthBytes() {
        return keyBits / 8;
    }

    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    public String curve() {
        return curve;
    }

    public String provider() {
        return provider;
    }

    public boolean gm() {
        return this == GM;
    }
}
