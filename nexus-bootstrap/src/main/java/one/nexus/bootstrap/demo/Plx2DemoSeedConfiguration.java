package one.nexus.bootstrap.demo;

import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * PLX2. One ordered pass for the new-model demo: the fixed tenant, PLA8's organization and position roles,
 * then PLB9's published leave product and subscription. The portal menu is written later at
 * {@link Plx2DemoSeedOrder#PORTAL}. A later start writes nothing new. The permission member-access switch is not changed.
 */
@Configuration
public class Plx2DemoSeedConfiguration {
    @Bean
    @Order(Plx2DemoSeedOrder.PATH)
    ApplicationRunner plx2DemoSeed(JdbcClient jdbc, IdentityModule identity, PermissionModule permissions,
            ObjectProvider<TenantMemberGrantSeeder> seeder, AppDesignService design, MarketService market,
            ProductCatalogService products, MarketRepository repository, MarketIsolation isolation, Environment env) {
        // The seeder bean only exists with nexus.permission.seed-tenant-member-grants=true; a hard dependency stopped
        // every context without it from starting. Resolve lazily and fall back to a local seeder for the demo pass.
        return args -> run(
                env.getProperty(DemoProfileGate.ENABLED),
                env.getProperty(DemoProfileGate.ENVIRONMENT, "local"),
                env.getProperty("spring.datasource.url", ""),
                jdbc, identity, permissions,
                seeder.getIfAvailable(() -> new TenantMemberGrantSeeder(permissions, identity.repository())),
                design, market, products, repository, isolation);
    }

    static void run(String enabled, String environment, String datasourceUrl, JdbcClient jdbc,
            IdentityModule identity, PermissionModule permissions, TenantMemberGrantSeeder seeder,
            AppDesignService design, MarketService market, ProductCatalogService products,
            MarketRepository repository, MarketIsolation isolation) {
        if (!DemoProfileGate.enabled(enabled)) {
            return;
        }
        DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
        P1DemoSeedConfiguration.seedTenant(jdbc);
        Pla8OrgDemoContent.write(identity, permissions, seeder, P1DemoSeedConfiguration.TENANT_ID);
        IdentityRepository people = identity.repository();
        Plb9CatalogDemoContent.write(design, market, products, repository, isolation, people, permissions,
                P1DemoSeedConfiguration.TENANT_ID);
    }
}
