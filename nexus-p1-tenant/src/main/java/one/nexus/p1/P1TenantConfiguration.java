package one.nexus.p1;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import one.nexus.p1.config.P1Properties;

/**
 * Library configuration scanned by {@code nexus-bootstrap}
 * ({@code @SpringBootApplication(scanBasePackages = "one.nexus")}).
 */
@Configuration
@EnableConfigurationProperties(P1Properties.class)
public class P1TenantConfiguration {
}
