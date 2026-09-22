package one.nexus.crypto.tls;

/**
 * Published GM algorithm identifiers used as safe test vectors. These are
 * public standard OIDs, not secrets.
 */
public final class GmTlsOids {
    private GmTlsOids() {}

    /** SM2 curve sm2p256v1 (GM/T 0003). */
    public static final String SM2_CURVE = "1.2.156.10197.1.301";

    /** SM3 (GM/T 0004). */
    public static final String SM3 = "1.2.156.10197.1.401";

    /** SM3withSM2 (GM/T 0003). */
    public static final String SM3_WITH_SM2 = "1.2.156.10197.1.501";

    /** SM4 (GM/T 0002). */
    public static final String SM4 = "1.2.156.10197.1.104";

    /** RFC 8998 TLS_SM4_GCM_SM3 = {0x00,0xC6}. */
    public static final int TLS_SM4_GCM_SM3 = 0x00c6;

    /** RFC 8998 TLS_SM4_CCM_SM3 = {0x00,0xC7}. */
    public static final int TLS_SM4_CCM_SM3 = 0x00c7;

    public static final String TLS_SM4_GCM_SM3_NAME = "TLS_SM4_GCM_SM3";

    public static final String SIG_ALG_JCA = "SM3withSM2";
}
