package one.nexus.bootstrap.profile;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Runs {@link ProductionProfileGuard} while singletons are created, so a bad
 * {@code prod} start fails before the web server accepts connections.
 */
@Configuration
public class ProductionProfileConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ProductionProfileConfiguration.class);

    @Bean
    ProductionProfileCheck productionProfileCheck(Environment env) {
        List<String> violations = ProductionProfileGuard.violations(env);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("prod profile refused to start:\n - " + String.join("\n - ", violations));
        }
        List<String> warnings = ProductionProfileGuard.warnings(env);
        warnings.forEach(warning -> log.warn(warning));
        return new ProductionProfileCheck(ProductionProfileGuard.productionActive(env), warnings);
    }

    public record ProductionProfileCheck(boolean production, List<String> warnings) { }
}
