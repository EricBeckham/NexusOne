package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.interfaces.ECPublicKey;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

class FixedVectorTest {
    @Test
    void sha256AndSm3Abc() throws Exception {
        assertEquals(FixedVectors.SHA256_ABC, Hex.encode(VectorSupport.sha256(FixedVectors.ABC)));
        assertEquals(FixedVectors.SM3_ABC, Hex.encode(VectorSupport.sm3(FixedVectors.ABC)));
        CryptoPort intl = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        CryptoPort gm = JcaCryptoPort.ephemeral(CryptoSuite.GM);
        assertEquals(FixedVectors.SHA256_ABC, Hex.encode(intl.digest(FixedVectors.ABC)));
        assertEquals(FixedVectors.SM3_ABC, Hex.encode(gm.digest(FixedVectors.ABC)));
    }

    @Test
    void hmacSha256Rfc4231() throws Exception {
        assertEquals(
                FixedVectors.HMAC_SHA256_TC1,
                Hex.encode(VectorSupport.hmacSha256(
                        Hex.decode(FixedVectors.HMAC_SHA256_KEY), FixedVectors.HMAC_SHA256_DATA)));
    }

    @Test
    void hmacSm3ProductFrozen() throws Exception {
        assertEquals(
                FixedVectors.HMAC_SM3_TC1,
                Hex.encode(VectorSupport.hmacSm3(
                        Hex.decode(FixedVectors.HMAC_SM3_KEY), VectorSupport.ascii("Hi There"))));
    }

    @Test
    void aes256GcmNistEmpty() throws Exception {
        byte[] tag = VectorSupport.aesGcm(new byte[32], new byte[12], new byte[0], new byte[0], Cipher.ENCRYPT_MODE);
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", Hex.encode(tag));
        byte[] opened = VectorSupport.aesGcm(new byte[32], new byte[12], new byte[0], tag, Cipher.DECRYPT_MODE);
        assertEquals(0, opened.length);
    }

    @Test
    void sm4EcbGmt0002() throws Exception {
        byte[] cipher = VectorSupport.sm4Ecb(
                Hex.decode(FixedVectors.SM4_ECB_KEY),
                Hex.decode(FixedVectors.SM4_ECB_PLAIN),
                Cipher.ENCRYPT_MODE);
        assertEquals(FixedVectors.SM4_ECB_CIPHER, Hex.encode(cipher));
        assertArrayEquals(
                Hex.decode(FixedVectors.SM4_ECB_PLAIN),
                VectorSupport.sm4Ecb(Hex.decode(FixedVectors.SM4_ECB_KEY), cipher, Cipher.DECRYPT_MODE));
    }

    @Test
    void sm4GcmProductFrozen() throws Exception {
        byte[] key = Hex.decode(FixedVectors.SM4_GCM_KEY);
        byte[] iv = Hex.decode(FixedVectors.SM4_GCM_IV);
        byte[] pt = Hex.decode(FixedVectors.SM4_GCM_PLAIN_HEX);
        byte[] sealed = VectorSupport.sm4Gcm(key, iv, new byte[0], pt, Cipher.ENCRYPT_MODE);
        String expected = FixedVectors.SM4_GCM_CIPHERTEXT + FixedVectors.SM4_GCM_TAG;
        assertEquals(expected, Hex.encode(sealed));
        assertArrayEquals(pt, VectorSupport.sm4Gcm(key, iv, new byte[0], sealed, Cipher.DECRYPT_MODE));
    }

    @Test
    void sm2SampleKeySignsAndMatchesPublishedPoint() throws Exception {
        var pair = VectorSupport.sm2SamplePair();
        String point = VectorSupport.publicPointHex((ECPublicKey) pair.getPublic());
        assertEquals(FixedVectors.SM2_PX + FixedVectors.SM2_PY, point);
        byte[] message = VectorSupport.ascii("message digest");
        byte[] proof = VectorSupport.sm2Sign(pair, message);
        assertTrue(VectorSupport.sm2Verify(pair, message, proof));
        byte[] changed = message.clone();
        changed[0] ^= 1;
        assertTrue(!VectorSupport.sm2Verify(pair, changed, proof));
    }
}
