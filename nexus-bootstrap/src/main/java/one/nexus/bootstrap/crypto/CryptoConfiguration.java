package one.nexus.bootstrap.crypto;

import java.security.SecureRandom;
import javax.sql.DataSource;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.Hex;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.crypto.Keyring;
import one.nexus.crypto.LocalThenEnvSecretPort;
import one.nexus.crypto.SecretPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfiguration {
    @Bean
    CryptoPort cryptoPort(
            @Value("${nexus.crypto.suite:INTERNATIONAL}") String suiteName,
            @Value("${nexus.crypto.key-source:ephemeral}") String keySource,
            @Value("${nexus.crypto.key-id:local}") String keyId,
            @Value("${nexus.crypto.key-version:1}") int keyVersion,
            @Value("${nexus.crypto.master-key:}") String masterKeyHex) {
        CryptoSuite suite = CryptoSuite.valueOf(suiteName.trim().toUpperCase(java.util.Locale.ROOT));
        if ("env".equalsIgnoreCase(keySource)) {
            if (masterKeyHex == null || masterKeyHex.isBlank()) {
                throw new IllegalStateException(
                        "NEXUS_CRYPTO_MASTER_KEY / nexus.crypto.master-key is required when key-source=env");
            }
            return JcaCryptoPort.fromMasterKey(suite, keyId, keyVersion, Hex.decode(masterKeyHex));
        }
        return new JcaCryptoPort(Keyring.ephemeral(suite, keyId, keyVersion, new SecureRandom()));
    }

    @Bean
    SecretPort secretPort(CryptoPort cryptoPort, DataSource dataSource) {
        return LocalThenEnvSecretPort.durableSystem(cryptoPort, dataSource);
    }
}
