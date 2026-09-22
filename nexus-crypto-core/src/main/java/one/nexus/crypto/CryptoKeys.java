package one.nexus.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import javax.crypto.SecretKey;

public record CryptoKeys(
        KeyRef ref, SecretKey aeadKey, SecretKey macKey, PrivateKey signingPrivate, PublicKey signingPublic) {
    public CryptoKeys {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(aeadKey, "aeadKey");
        Objects.requireNonNull(macKey, "macKey");
        Objects.requireNonNull(signingPrivate, "signingPrivate");
        Objects.requireNonNull(signingPublic, "signingPublic");
    }
}
