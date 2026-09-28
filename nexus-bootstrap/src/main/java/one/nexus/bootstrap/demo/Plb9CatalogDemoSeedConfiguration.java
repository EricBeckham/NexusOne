package one.nexus.bootstrap.demo;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * PLB9 gate. Boot no longer starts this by itself: {@link Plx2DemoSeedConfiguration} publishes the leave product
 * after PLA8. Tests still call {@link #run}. The tenant row is inserted here as well, because this entry can run alone.
 */
public final class Plb9CatalogDemoSeedConfiguration {
    private Plb9CatalogDemoSeedConfiguration() {}

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
