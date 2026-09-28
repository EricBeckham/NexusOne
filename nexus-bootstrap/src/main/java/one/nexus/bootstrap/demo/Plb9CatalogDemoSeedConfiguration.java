package one.nexus.bootstrap.demo;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * PLB9. When the demo switch is on, publish the leave product and subscribe the fixed tenant through the market APIs.
 * Runs after PLA8 ({@code @Order(1000)}) so the positions exist. The P1 tenant seed has no order of its own and can
 * run later, so this runner makes sure that tenant row exists first.
 */
@Configuration
public class Plb9CatalogDemoSeedConfiguration {
    @Bean
    @Order(1100)
    ApplicationRunner plb9CatalogDemoSeed(AppDesignService design, MarketService market, ProductCatalogService products,
            MarketRepository repository, MarketIsolation isolation, IdentityRepository identity,
            PermissionModule permissions, JdbcClient jdbc, Environment env) {
        return args -> run(
                env.getProperty(DemoProfileGate.ENABLED),
                env.getProperty(DemoProfileGate.ENVIRONMENT, "local"),
                env.getProperty("spring.datasource.url", ""),
                design, market, products, repository, isolation, identity, permissions, jdbc);
    }

    static void run(String enabled, String environment, String datasourceUrl, AppDesignService design,
            MarketService market, ProductCatalogService products, MarketRepository repository,
            MarketIsolation isolation, IdentityRepository identity, PermissionModule permissions, JdbcClient jdbc) {
        if (!DemoProfileGate.enabled(enabled)) {
            return;
        }
        DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
        P1DemoSeedConfiguration.seedTenant(jdbc);
        Plb9CatalogDemoContent.write(design, market, products, repository, isolation, identity, permissions,
                P1DemoSeedConfiguration.TENANT_ID);
    }
}
