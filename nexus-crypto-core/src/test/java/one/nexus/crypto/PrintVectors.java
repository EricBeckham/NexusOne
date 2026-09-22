package one.nexus.crypto;

/**
 * One-shot printer for product-frozen SM4-GCM / HMAC-SM3 vectors.
 * Run: {@code mvn -pl nexus-crypto-core -DskipTests exec} is not wired;
 * invoked from {@link FixedVectorTest} setup comments and {@code PrintVectorsTest}.
 */
public final class PrintVectors {
    public static void main(String[] args) throws Exception {
        byte[] hmac = VectorSupport.hmacSm3(Hex.decode(FixedVectors.HMAC_SM3_KEY), VectorSupport.ascii("Hi There"));
        System.out.println("HMAC_SM3_TC1=" + Hex.encode(hmac));
        byte[] gcm = VectorSupport.sm4Gcm(
                Hex.decode(FixedVectors.SM4_GCM_KEY),
                Hex.decode(FixedVectors.SM4_GCM_IV),
                new byte[0],
                Hex.decode(FixedVectors.SM4_GCM_PLAIN_HEX),
                javax.crypto.Cipher.ENCRYPT_MODE);
        System.out.println("SM4_GCM_CT_TAG=" + Hex.encode(gcm));
        byte[] aes = VectorSupport.aesGcm(
                new byte[32],
                new byte[12],
                new byte[0],
                new byte[0],
                javax.crypto.Cipher.ENCRYPT_MODE);
        System.out.println("AES256_GCM_ZERO_TAG=" + Hex.encode(aes));
    }
}
