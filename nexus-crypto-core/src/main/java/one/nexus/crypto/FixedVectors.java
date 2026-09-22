package one.nexus.crypto;

/**
 * Published and product-frozen test vectors for CryptoPort.
 *
 * <p>SM3 / SHA-256 / HMAC-SHA256 / AES-GCM / SM4-ECB values are from public
 * standards. SM4-GCM and HMAC-SM3 values are frozen against Bouncy Castle
 * {@code bcprov-jdk18on:1.85.2} on Java 21 (same provider path as P0-10).
 */
public final class FixedVectors {
    private FixedVectors() {}

    public static final byte[] ABC = {'a', 'b', 'c'};

    /** FIPS 180-4 SHA-256("abc"). */
    public static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** GM/T 0004-2012 SM3("abc"). */
    public static final String SM3_ABC = "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

    /** RFC 4231 HMAC-SHA256 test case 1. */
    public static final String HMAC_SHA256_KEY = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b";

    public static final byte[] HMAC_SHA256_DATA = "Hi There".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public static final String HMAC_SHA256_TC1 =
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7";

    /** NIST SP 800-38D AES-256-GCM (empty AAD, 96-bit IV). */
    /** NIST SP 800-38D AES-256-GCM, 96-bit IV, empty AAD and empty plaintext. */
    public static final String AES256_GCM_ZERO_TAG = "530f8afbc74536b9a963b4f1c4cb738b";

    /** GM/T 0002-2012 SM4-ECB one-block example. */
    public static final String SM4_ECB_KEY = "0123456789abcdeffedcba9876543210";

    public static final String SM4_ECB_PLAIN = "0123456789abcdeffedcba9876543210";

    public static final String SM4_ECB_CIPHER = "681edf34d206965e86b3e94f536e4246";

    /**
     * SM4-GCM product vector: key/iv from GM/T 0002 example key + 12-byte IV,
     * plaintext "abc", AAD empty. Frozen on BC 1.85.2 / Java 21.
     */
    public static final String SM4_GCM_KEY = "0123456789abcdeffedcba9876543210";

    public static final String SM4_GCM_IV = "000000000000000000000000";

    public static final String SM4_GCM_PLAIN_HEX = "616263";

    public static final String SM4_GCM_CIPHERTEXT = "d2710f";

    public static final String SM4_GCM_TAG = "5106ba1dc0d73255f3700f658f6f7f1e";

    /** HMAC-SM3 with 32-byte key 0x0b.. and "Hi There", frozen on BC 1.85.2 / Java 21. */
    public static final String HMAC_SM3_KEY =
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b";

    public static final String HMAC_SM3_TC1 = "c0ba18c68b90c88bc07de794bfc7d2c8d19ec31ed8773bc2b390c9604e0be11e";

    /** GM/T 0003-2012 recommended SM2 key pair (signature example). */
    public static final String SM2_D = "3945208f7b2144b13f36e38ac6d39f95889393692860b51a42fb81ef4df7c5b8";

    public static final String SM2_PX = "09f9df311e5421a150dd7d161e4bc5c672179fad1833fc076bb08ff356f35020";

    public static final String SM2_PY = "ccea490ce26775a52dc6ea718cc1aa600aed05fbf35e084a6632f6072da9ad13";
}
