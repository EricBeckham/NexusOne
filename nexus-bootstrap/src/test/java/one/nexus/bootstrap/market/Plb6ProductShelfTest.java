package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.CatalogGroup;
import one.nexus.p4.market.MarketService;
import one.nexus.p4.market.ProductCatalogService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PLB6: product shelves group by application category and leave the catalog when archived. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb6ProductShelfTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired MarketService market;
    @Autowired ProductCatalogService products;

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

    @AfterEach
    void clear() { IsolationContextHolder.clear(); }

    @Test
    void publishedProductSkusGroupByCategoryAndReturnAfterRelist() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        category("oa" + suffix, "办公", 1);
        category("hr" + suffix, "人事", 2);
        UUID office = product("off" + suffix, "请假", "oa" + suffix, "sku_off_" + suffix, "PUBLISHED");
        product("hum" + suffix, "人事", "hr" + suffix, "sku_hr_" + suffix, "PUBLISHED");
        product("drf" + suffix, "草稿", "oa" + suffix, "sku_drf_" + suffix, "DRAFT");

        assertEquals(java.util.List.of("oa" + suffix, "hr" + suffix),
                market.catalogGroups().stream().map(CatalogGroup::categoryCode).toList());
        CatalogGroup officeGroup = market.catalogGroups().get(0);
        assertEquals("办公", officeGroup.categoryName());
        assertEquals("sku_off_" + suffix, officeGroup.products().getFirst().skus().getFirst().code());
        assertTrue(market.catalogGroups().stream().noneMatch(group -> group.products().stream().anyMatch(item -> "drf".concat(suffix).equals(item.code()))));

        asPlatform();
        products.archive(office, 1);
        assertEquals(java.util.List.of("hr" + suffix), market.catalogGroups().stream().map(CatalogGroup::categoryCode).toList());

        products.publish(office, 2);
        assertEquals(java.util.List.of("oa" + suffix, "hr" + suffix),
                market.catalogGroups().stream().map(CatalogGroup::categoryCode).toList());
    }

    private void category(String code, String name, int sort) {
        jdbc.sql("""
                INSERT INTO market.app_category(code,name,sort_order,created_at,updated_at,version)
                VALUES(:code,:name,:sort,now(),now(),1)
                """).param("code", code).param("name", name).param("sort", sort).update();
    }

    private UUID product(String code, String name, String category, String sku, String status) {
        UUID app = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,category_code,created_at) VALUES(:id,:code,:name,:category,now())")
                .param("id", app).param("code", code).param("name", name).param("category", category).update();
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at,manifest)
                VALUES(:id,:app,'1.0.0','PUBLISHED',now(),now(),'{"schema":"nexus.app-manifest.v1"}'::jsonb)
                """).param("id", version).param("app", app).update();
        jdbc.sql("""
                INSERT INTO market.product(id,code,name,status,created_at,updated_at,version)
                VALUES(:id,:code,:name,:status,now(),now(),1)
                """).param("id", id).param("code", code).param("name", name).param("status", status).update();
        jdbc.sql("""
                INSERT INTO market.product_app(product_id,app_id,app_version_id,sort_order)
                VALUES(:product,:app,:version,0)
                """).param("product", id).param("app", app).param("version", version).update();
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,product_id,name,entitlements)
                VALUES(:code,NULL,:product,:name,'{"apps":{"enabled":true,"quota":5,"attributes":{}}}'::jsonb)
                """).param("code", sku).param("product", id).param("name", name).update();
        return id;
    }

    private void asPlatform() {
        UUID home = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        var actor = new one.nexus.p1.domain.Actor(person, home, "Platform", Set.of(PlatformDuty.SYSTEM_ADMIN));
        IsolationContextHolder.set(new IsolationSnapshot(actor,
                new PlatformContext(true, "authenticated platform content operation", actor, "plb6-trace", home), null, "plb6-trace"));
    }
}
