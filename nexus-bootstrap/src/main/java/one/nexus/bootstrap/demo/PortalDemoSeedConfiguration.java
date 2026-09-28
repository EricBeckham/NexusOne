package one.nexus.bootstrap.demo;

import one.nexus.p3.portal.PortalConfigService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * PLC9. When the demo switch is on, replace the fixed tenant's portal menu and home modules
 * through {@link PortalConfigService}. Runs after {@link Plx2DemoSeedConfiguration} so the leave
 * subscription exists before the menu is written. A later start writes the same rows again.
 */
@Configuration
public class PortalDemoSeedConfiguration {
    @Bean
    @Order(Plx2DemoSeedOrder.PORTAL)
    ApplicationRunner portalDemoSeed(PortalConfigService config, Environment env) {
        return args -> run(
                env.getProperty(DemoProfileGate.ENABLED),
                env.getProperty(DemoProfileGate.ENVIRONMENT, "local"),
                env.getProperty("spring.datasource.url", ""),
                config);
    }

    static void run(String enabled, String environment, String datasourceUrl, PortalConfigService config) {
        if (!DemoProfileGate.enabled(enabled)) {
            return;
        }
        DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
        PortalDemoContent.write(config, P1DemoSeedConfiguration.TENANT_ID);
    }
}
