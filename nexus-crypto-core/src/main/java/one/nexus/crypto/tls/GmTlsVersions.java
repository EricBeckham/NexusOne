package one.nexus.crypto.tls;

import java.security.Provider;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

/**
 * Exact runtime identities recorded in G17 GM TLS evidence. Not a product claim.
 */
public final class GmTlsVersions {
    private GmTlsVersions() {}

    public static String javaRuntime() {
        return Runtime.version().toString();
    }

    public static String bcProvider() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        return provider == null ? "BC-missing" : provider.toString();
    }

    public static String bcTls() {
        return JcaTlsCryptoProvider.class.getPackage().getImplementationVersion() == null
                ? "bctls-jdk18on:1.85"
                : "bctls-jdk18on:" + JcaTlsCryptoProvider.class.getPackage().getImplementationVersion();
    }

    public static String bcProvArtifact() {
        return "bcprov-jdk18on:1.85.2";
    }

    public static String summary() {
        return "jdk=" + javaRuntime() + " " + bcProvArtifact() + " " + bcProvider() + " " + bcTls();
    }
}
