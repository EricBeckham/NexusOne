package one.nexus.crypto;

import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class Providers {
    static final SecureRandom RANDOM = new SecureRandom();

    static {
        Provider existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (existing == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Providers() {}

    static MessageDigest messageDigest(String algorithm, String provider) throws java.security.GeneralSecurityException {
        return provider == null ? MessageDigest.getInstance(algorithm) : MessageDigest.getInstance(algorithm, provider);
    }

    static Mac mac(String algorithm, String provider) throws java.security.GeneralSecurityException {
        return provider == null ? Mac.getInstance(algorithm) : Mac.getInstance(algorithm, provider);
    }

    static Cipher cipher(String algorithm, String provider) throws java.security.GeneralSecurityException {
        return provider == null ? Cipher.getInstance(algorithm) : Cipher.getInstance(algorithm, provider);
    }

    static Signature signature(String algorithm, String provider) throws java.security.GeneralSecurityException {
        return provider == null ? Signature.getInstance(algorithm) : Signature.getInstance(algorithm, provider);
    }

    static KeyGenerator keyGenerator(String algorithm, String provider) throws java.security.GeneralSecurityException {
        return provider == null ? KeyGenerator.getInstance(algorithm) : KeyGenerator.getInstance(algorithm, provider);
    }

    static KeyPairGenerator keyPairGenerator(String algorithm, String provider)
            throws java.security.GeneralSecurityException {
        return provider == null
                ? KeyPairGenerator.getInstance(algorithm)
                : KeyPairGenerator.getInstance(algorithm, provider);
    }
}
