package one.nexus.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;

final class VectorSupport {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private VectorSupport() {}

    static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    static byte[] sm3(byte[] data) throws Exception {
        return MessageDigest.getInstance("SM3", "BC").digest(data);
    }

    static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] hmacSm3(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSM3", "BC");
        mac.init(new SecretKeySpec(key, "HmacSM3"));
        return mac.doFinal(data);
    }

    static byte[] aesGcm(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(plaintext);
    }

    static byte[] sm4Gcm(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", "BC");
        cipher.init(mode, new SecretKeySpec(key, "SM4"), new GCMParameterSpec(128, nonce));
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(plaintext);
    }

    static byte[] sm4Ecb(byte[] key, byte[] input, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("SM4/ECB/NoPadding", "BC");
        cipher.init(mode, new SecretKeySpec(key, "SM4"));
        return cipher.doFinal(input);
    }

    static KeyPair sm2SamplePair() throws Exception {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("sm2p256v1");
        ECParameterSpec jca = new ECNamedCurveSpec(spec.getName(), spec.getCurve(), spec.getG(), spec.getN(), spec.getH());
        java.math.BigInteger d = new java.math.BigInteger(1, Hex.decode(FixedVectors.SM2_D));
        java.math.BigInteger x = new java.math.BigInteger(1, Hex.decode(FixedVectors.SM2_PX));
        java.math.BigInteger y = new java.math.BigInteger(1, Hex.decode(FixedVectors.SM2_PY));
        KeyFactory factory = KeyFactory.getInstance("EC", "BC");
        var privateKey = factory.generatePrivate(new ECPrivateKeySpec(d, jca));
        var publicKey = factory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), jca));
        return new KeyPair(publicKey, privateKey);
    }

    static byte[] sm2Sign(KeyPair pair, byte[] message) throws Exception {
        Signature signature = Signature.getInstance("SM3withSM2", "BC");
        signature.initSign(pair.getPrivate());
        signature.update(message);
        return signature.sign();
    }

    static boolean sm2Verify(KeyPair pair, byte[] message, byte[] proof) throws Exception {
        Signature signature = Signature.getInstance("SM3withSM2", "BC");
        signature.initVerify(pair.getPublic());
        signature.update(message);
        return signature.verify(proof);
    }

    static String publicPointHex(ECPublicKey key) {
        ECPoint point = key.getW();
        return Hex.encode(toUnsigned(point.getAffineX())) + Hex.encode(toUnsigned(point.getAffineY()));
    }

    private static byte[] toUnsigned(java.math.BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        if (raw.length == 33 && raw[0] == 0) {
            byte[] out = new byte[32];
            System.arraycopy(raw, 1, out, 0, 32);
            return out;
        }
        if (raw.length < 32) {
            byte[] out = new byte[32];
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
            return out;
        }
        throw new IllegalStateException("unexpected coordinate length " + raw.length);
    }

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
