package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.p4.market.ProductCatalogService.ProductApp;
import one.nexus.p4.market.ProductCatalogService.ProductWrite;
import one.nexus.p4.market.ProductCatalogService.SkuWrite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** PLB5 schema and product/SKU APIs against disposable loopback PostgreSQL. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb5ProductSkuPersistenceTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired ProductCatalogService products;
    @Autowired MarketRepository market;
    @MockitoBean IsolationGuard isolation;

    @BeforeAll static void startPostgres() throws Exception { postgres = EmbeddedPostgres.builder().start(); }
    @AfterAll static void stopPostgres() throws Exception { if (postgres != null) postgres.close(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.demo.enabled", () -> "false");
    }

    @Test void productCombinesPinnedAppsAndSkuPointsToProductWhileLegacySkuAutoCreatesOneAppProduct() {
        UUID app1 = UUID.randomUUID(), app2 = UUID.randomUUID();
        UUID version1 = UUID.randomUUID(), version2 = UUID.randomUUID();
        seedApp(app1, version1, "plb5a");
        seedApp(app2, version2, "plb5b");

        var draft = products.create(new ProductWrite("plb5_combo", "组合产品", null, null));
        assertEquals("DRAFT", draft.product().status());
        assertThrows(ResponseStatusException.class, () -> products.publish(draft.product().id(), 1));
        assertThrows(ResponseStatusException.class, () -> products.replaceApps(draft.product().id(), 1,
                List.of(new ProductApp(app1, version2, 0))));
        var composed = products.replaceApps(draft.product().id(), 1,
                List.of(new ProductApp(app1, version1, 0), new ProductApp(app2, version2, 1)));
        assertEquals(2, composed.apps().size());
        assertEquals(2, composed.product().version());
        var published = products.publish(draft.product().id(), 2);
        assertEquals("PUBLISHED", published.product().status());
        assertThrows(ResponseStatusException.class, () -> products.replaceApps(draft.product().id(), 3,
                List.of(new ProductApp(app1, version1, 0))));

        Map<String, EntitlementSpec> rights = Map.of("apps", new EntitlementSpec(true, 2, Map.of()));
        products.createSku(draft.product().id(), new SkuWrite("plb5_combo_sku", "组合标准版", rights));
        assertTrue(jdbc.sql("SELECT app_id IS NULL FROM market.sku WHERE code='plb5_combo_sku'")
                .query(Boolean.class).single());
        assertEquals(draft.product().id(), jdbc.sql("SELECT product_id FROM market.sku WHERE code='plb5_combo_sku'")
                .query(UUID.class).single());

        market.saveSku(new Sku("plb5_legacy_sku", app1, "旧单应用", rights));
        UUID autoProduct = jdbc.sql("SELECT product_id FROM market.sku WHERE code='plb5_legacy_sku'")
                .query(UUID.class).single();
        assertNotNull(autoProduct);
        assertEquals(1, jdbc.sql("SELECT count(*) FROM market.product_app WHERE product_id=:id")
                .param("id", autoProduct).query(Integer.class).single());
        market.saveSku(new Sku("plb5_legacy_sku", app1, "旧单应用", rights));
        assertEquals(autoProduct, jdbc.sql("SELECT product_id FROM market.sku WHERE code='plb5_legacy_sku'")
                .query(UUID.class).single());
        assertThrows(IllegalStateException.class,
                () -> market.saveSku(new Sku("plb5_legacy_sku", app2, "错误改绑", rights)));
    }

    private void seedApp(UUID appId, UUID versionId, String code) {
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES (:id,:code,:name,now())")
                .param("id", appId).param("code", code).param("name", code).update();
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at,manifest)
                VALUES(:id,:app,'1.0.0','PUBLISHED',now(),now(),'{"schema":"nexus.app-manifest.v1"}'::jsonb)
                """).param("id", versionId).param("app", appId).update();
    }
}
