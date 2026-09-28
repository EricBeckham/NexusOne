package one.nexus.bootstrap.demo;

import one.nexus.p3.portal.PortalConfigService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * PLC9. When the demo switch is on, replace the fixed tenant's portal menu and home modules
 * through {@link PortalConfigService}. A later start with the switch still on writes the same rows again.
 * This does not register an application-instance query; that stays empty until PLB7.
 */
@Configuration
public class PortalDemoSeedConfiguration {
    @Bean
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
