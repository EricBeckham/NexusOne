package poc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class CryptoContract {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoContract() { }

    interface CryptoPort {
        byte[] digest(byte[] data) throws Exception;
        byte[] mac(byte[] data) throws Exception;
        byte[] encrypt(byte[] data) throws Exception;
        byte[] decrypt(byte[] sealed) throws Exception;
        byte[] sign(byte[] data) throws Exception;
        boolean verify(byte[] data, byte[] signature) throws Exception;
    }

    static final class JcaPort implements CryptoPort {
        private final String digestAlgorithm;
        private final String macAlgorithm;
        private final String cipherAlgorithm;
        private final String signatureAlgorithm;
        private final String provider;
        private final SecretKey encryptionKey;
        private final SecretKey macKey;
        private final KeyPair signingKey;

        JcaPort(boolean gm) throws Exception {
            provider = gm ? "BC" : null;
            digestAlgorithm = gm ? "SM3" : "SHA-256";
            macAlgorithm = gm ? "HmacSM3" : "HmacSHA256";
            cipherAlgorithm = gm ? "SM4/GCM/NoPadding" : "AES/GCM/NoPadding";
            signatureAlgorithm = gm ? "SM3withSM2" : "SHA256withECDSA";
            KeyGenerator kg = provider == null ? KeyGenerator.getInstance(gm ? "SM4" : "AES") : KeyGenerator.getInstance("SM4", provider);
            kg.init(gm ? 128 : 256, RANDOM);
            encryptionKey = kg.generateKey();
            KeyGenerator mk = provider == null ? KeyGenerator.getInstance(macAlgorithm) : KeyGenerator.getInstance(macAlgorithm, provider);
            macKey = mk.generateKey();
            KeyPairGenerator kpg = provider == null ? KeyPairGenerator.getInstance("EC") : KeyPairGenerator.getInstance("EC", provider);
            kpg.initialize(new ECGenParameterSpec(gm ? "sm2p256v1" : "secp256r1"), RANDOM);
            signingKey = kpg.generateKeyPair();
        }

        private Cipher cipher() throws Exception {
            return provider == null ? Cipher.getInstance(cipherAlgorithm) : Cipher.getInstance(cipherAlgorithm, provider);
        }

        private Signature signature() throws Exception {
            return provider == null ? Signature.getInstance(signatureAlgorithm) : Signature.getInstance(signatureAlgorithm, provider);
        }

        @Override public byte[] digest(byte[] data) throws Exception {
            MessageDigest md = provider == null ? MessageDigest.getInstance(digestAlgorithm) : MessageDigest.getInstance(digestAlgorithm, provider);
            return md.digest(data);
        }

        @Override public byte[] mac(byte[] data) throws Exception {
            Mac m = provider == null ? Mac.getInstance(macAlgorithm) : Mac.getInstance(macAlgorithm, provider);
            m.init(macKey);
            return m.doFinal(data);
        }

        @Override public byte[] encrypt(byte[] data) throws Exception {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher c = cipher();
            c.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            byte[] payload = c.doFinal(data);
            byte[] out = Arrays.copyOf(nonce, nonce.length + payload.length);
            System.arraycopy(payload, 0, out, nonce.length, payload.length);
            return out;
        }

        @Override public byte[] decrypt(byte[] sealed) throws Exception {
            byte[] nonce = Arrays.copyOfRange(sealed, 0, 12);
            byte[] payload = Arrays.copyOfRange(sealed, 12, sealed.length);
            Cipher c = cipher();
            c.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            return c.doFinal(payload);
        }

        @Override public byte[] sign(byte[] data) throws Exception {
            Signature s = signature();
            s.initSign(signingKey.getPrivate(), RANDOM);
            s.update(data);
            return s.sign();
        }

        @Override public boolean verify(byte[] data, byte[] proof) throws Exception {
            Signature s = signature();
            s.initVerify(signingKey.getPublic());
            s.update(data);
            return s.verify(proof);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void exercise(String suite, CryptoPort port) throws Exception {
        byte[] message = "NexusOne P0-10 crypto contract".getBytes(StandardCharsets.UTF_8);
        byte[] changed = message.clone();
        changed[0] ^= 1;
        check(!Arrays.equals(port.digest(message), port.digest(changed)), suite + " digest tamper detection");
        check(!Arrays.equals(port.mac(message), port.mac(changed)), suite + " MAC tamper detection");
        byte[] sealed = port.encrypt(message);
        check(Arrays.equals(message, port.decrypt(sealed)), suite + " decrypt roundtrip");
        sealed[sealed.length - 1] ^= 1;
        boolean rejected = false;
        try { port.decrypt(sealed); } catch (Exception expected) { rejected = true; }
        check(rejected, suite + " ciphertext tamper rejection");
        byte[] proof = port.sign(message);
        check(port.verify(message, proof), suite + " signature roundtrip");
        check(!port.verify(changed, proof), suite + " signature tamper rejection");
        System.out.println("PASS: " + suite + " digest, MAC, AEAD and signature contract");
    }

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        exercise("international", new JcaPort(false));
        exercise("GM", new JcaPort(true));
    }
}
