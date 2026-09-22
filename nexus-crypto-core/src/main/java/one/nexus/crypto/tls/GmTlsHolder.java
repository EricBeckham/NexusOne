package one.nexus.crypto.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

/** One SM2 certificate plus its private key and chain (leaf first). */
public record GmTlsHolder(
        GmTlsRole role,
        String alias,
        X509Certificate certificate,
        PrivateKey privateKey,
        X509Certificate[] chain) {
    public GmTlsHolder {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(certificate, "certificate");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(chain, "chain");
        if (chain.length < 1) {
            throw new IllegalArgumentException("chain must include the leaf");
        }
    }
}
