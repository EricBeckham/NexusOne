package one.nexus.bootstrap.tls;

import java.nio.file.Path;
import one.nexus.crypto.tls.GmTlsDualMaterial;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in loader for local GM dual-cert PKCS12 files. Default HTTP connector is
 * unchanged. Enable only with {@code nexus.tls.gm.enabled=true} and a local dir.
 */
@Configuration
@ConditionalOnProperty(prefix = "nexus.tls.gm", name = "enabled", havingValue = "true")
public class GmTlsConfiguration {
    @Bean
    GmTlsDualMaterial gmTlsDualMaterial(
            @Value("${nexus.tls.gm.dir:.local/g17-gm-tls}") String directory,
            @Value("${NEXUS_GM_TLS_STORE_PASSWORD:}") String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "NEXUS_GM_TLS_STORE_PASSWORD is required when nexus.tls.gm.enabled=true");
        }
        try {
            return GmTlsDualMaterial.load(Path.of(directory), password.toCharArray());
        } catch (Exception ex) {
            throw new IllegalStateException("unable to load GM TLS dual-cert material from " + directory, ex);
        }
    }
}
