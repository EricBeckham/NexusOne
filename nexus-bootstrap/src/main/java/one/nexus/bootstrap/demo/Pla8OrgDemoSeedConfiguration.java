package one.nexus.bootstrap.demo;

import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * PLA8. When the demo switch is on, create the fixed tenant's office, positions, people and position-role
 * bindings through the identity and permission APIs. A later start writes nothing new.
 * Switching {@code NEXUS_PERMISSION_MEMBER_ACCESS} to {@code position} waits until the application roles exist.
 */
@Configuration
public class Pla8OrgDemoSeedConfiguration {
    @Bean
    @Order(1000)
    ApplicationRunner pla8OrgDemoSeed(IdentityModule identity, PermissionModule permissions,
            TenantMemberGrantSeeder seeder, Environment env) {
        return args -> run(
                env.getProperty(DemoProfileGate.ENABLED),
                env.getProperty(DemoProfileGate.ENVIRONMENT, "local"),
                env.getProperty("spring.datasource.url", ""),
                identity,
                permissions,
                seeder);
    }

    static void run(String enabled, String environment, String datasourceUrl, IdentityModule identity,
            PermissionModule permissions, TenantMemberGrantSeeder seeder) {
        if (!DemoProfileGate.enabled(enabled)) {
            return;
        }
        DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
        Pla8OrgDemoContent.write(identity, permissions, seeder, P1DemoSeedConfiguration.TENANT_ID);
    }
}
