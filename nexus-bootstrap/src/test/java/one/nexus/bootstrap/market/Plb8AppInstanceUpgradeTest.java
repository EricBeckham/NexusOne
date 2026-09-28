package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.instance.AppInstanceQuery;
import one.nexus.p3.instance.InstanceEvent;
import one.nexus.p3.instance.InstanceEvents;
import one.nexus.p4.market.AppInstanceService;
import one.nexus.p4.market.AppInstanceService.AppInstance;
import one.nexus.p4.market.AppInstanceService.UpgradeResult;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.ProductCatalogService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.pack.RolePackService.AppliedRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PLB8: manual upgrade, upgrade back, uninstall, and same-major automatic upgrade. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb8AppInstanceUpgradeTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired AppInstanceService instances;
    @Autowired AppInstanceQuery query;
    @Autowired MarketIsolation marketIsolation;
    @Autowired PermissionModule permissions;
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

    String suffix;
    String code;
    String grant;
    UUID appId;
    UUID v1;
    UUID productId;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        code = "lv" + suffix;
        grant = "fn." + code + ".apply";
        appId = UUID.randomUUID();
        v1 = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES(:id,:code,:code,now())")
                .param("id", appId).param("code", code).update();
        insertVersion(v1, "1.0.0", List.of("read"));
        productId = product("pl" + suffix, "sku_" + suffix);
    }

    @AfterEach
    void unbind() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
        IsolationContextHolder.clear();
    }

    @Test
    void tenantUpgradeThenBackThenUninstallBumpsRevisionAndKeepsDroppedGrants() {
        UUID tenant = tenant();
        asTenant(tenant);
        AppInstance installed = instances.subscribe(tenant, "sku_" + suffix).getFirst();
        UUID v11 = UUID.randomUUID();
        insertVersion(v11, "1.1.0", List.of("read", "write"));

        UpgradeResult upgraded = instances.upgrade(installed.id(), v11);
        assertEquals(2, upgraded.instance().revision());
        assertEquals("1.1.0", upgraded.instance().version());
        assertEquals(List.of("fn:" + code + ":apply", "fn:" + code + ":extra"), upgraded.instance().entries());
        assertTrue(holds(tenant, grant, "write"));

        InstanceEvent event = InstanceEvents.parse(events(tenant).get(1));
        assertEquals(InstanceEvent.Kind.UPGRADED, event.kind());
        assertEquals("1.0.0", event.previousVersion());
        assertEquals(2, event.revision());

        UpgradeResult back = instances.upgrade(installed.id(), v1);
        assertEquals(3, back.instance().revision());
        assertEquals("1.0.0", back.instance().version());
        assertTrue(back.rolePack().stream().map(AppliedRole::grantsNotInPack).anyMatch(list -> list.contains(grant + ":write:SELF")));
        assertTrue(holds(tenant, grant, "write"), "a grant the new pack dropped stays");
        assertTrue(holds(tenant, grant, "read"));

        AppInstance removed = instances.uninstall(installed.id());
        assertEquals(4, removed.revision());
        assertEquals("UNINSTALLED", removed.status());
        assertNull(removed.mode());
        assertTrue(removed.entries().isEmpty());
        InstanceEvent uninstalled = InstanceEvents.parse(events(tenant).get(3));
        assertEquals(InstanceEvent.Kind.UNINSTALLED, uninstalled.kind());
        assertNull(uninstalled.previousVersion());
        assertEquals("CANCELLED", jdbc.sql("SELECT status FROM market.subscription WHERE id=:id")
                .param("id", installed.subscriptionId()).query(String.class).single());
        assertEquals("UNINSTALLED", query.current(installed.id()).orElseThrow().status());
        assertThrows(RuntimeException.class, () -> instances.uninstall(installed.id()));
    }

    @Test
    void sameMajorVersionsFollowTheProductSwitchAndAMajorBumpStaysManual() {
        UUID tenant = tenant();
        asTenant(tenant);
        AppInstance installed = instances.subscribe(tenant, "sku_" + suffix).getFirst();
        UUID minor = UUID.randomUUID();
        insertVersion(minor, "1.1.0", List.of("read"));
        assertEquals(1, events(tenant).size());

        asPlatformOperator();
        products.setAutoUpgradeMinor(productId, true, 1);

        asTenant(tenant);
        AppInstance after = queryInstance(installed.id());
        assertEquals(2, after.revision());
        assertEquals("1.1.0", after.version());
        assertEquals(1, events(tenant).size(), "platform catch-up emits no event");
        assertEquals(Boolean.TRUE, jdbc.sql("SELECT auto_upgrade_minor FROM market.product WHERE id=:id")
                .param("id", productId).query(Boolean.class).single());

        UUID major = UUID.randomUUID();
        insertVersion(major, "2.0.0", List.of("read"));
        asPlatformOperator();
        instances.autoUpgradePublished(major);
        asTenant(tenant);
        assertEquals("1.1.0", queryInstance(installed.id()).version());
        var offer = instances.upgradeOffers().stream().filter(item -> installed.id().equals(item.instanceId())).findFirst().orElseThrow();
        assertEquals(major, offer.targetVersionId());
        assertTrue(!offer.minor());

        jdbc.sql("UPDATE market.app_instance SET mode='FORK' WHERE id=:id").param("id", installed.id()).update();
        assertThrows(RuntimeException.class, () -> instances.upgrade(installed.id(), major));
    }

    private AppInstance queryInstance(UUID id) {
        return instances.current().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private void insertVersion(UUID id, String version, List<String> actions) {
        String grants = actions.stream()
                .map(action -> "{\"resource\":\"%s\",\"action\":\"%s\",\"scope\":\"SELF\"}".formatted(grant, action))
                .reduce((a, b) -> a + "," + b).orElse("");
        String extra = "1.0.0".equals(version) ? "" : ",{\"key\":\"fn:%1$s:extra\",\"app_code\":\"%1$s\",\"form_code\":\"extra\",\"kind\":\"LIST\"}".formatted(code);
        String manifest = """
                {"schema":"nexus.app-manifest.v1","app_code":"%1$s","version":"%2$s","ledgers":[],"forms":[],"processes":[],
                 "role_pack":[{"code":"applicant","name":"申请人","grants":[%3$s]}],
                 "entries":[{"key":"fn:%1$s:apply","app_code":"%1$s","form_code":"apply","kind":"FILL"}%4$s]}
                """.formatted(code, version, grants, extra);
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at,manifest)
                VALUES(:id,:app,:version,'PUBLISHED',now(),now(),CAST(:manifest AS jsonb))
                """).param("id", id).param("app", appId).param("version", version).param("manifest", manifest).update();
    }

    private UUID product(String productCode, String sku) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO market.product(id,code,name,status,created_at,updated_at,version)
                VALUES(:id,:code,:code,'PUBLISHED',now(),now(),1)
                """).param("id", id).param("code", productCode).update();
        jdbc.sql("""
                INSERT INTO market.product_app(product_id,app_id,app_version_id,sort_order)
                VALUES(:product,:app,:version,0)
                """).param("product", id).param("app", appId).param("version", v1).update();
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,product_id,name,entitlements)
                VALUES(:code,NULL,:product,:code,'{"apps":{"enabled":true,"quota":5,"attributes":{}}}'::jsonb)
                """).param("code", sku).param("product", id).update();
        return id;
    }

    private UUID tenant() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO platform.tenant(id,code,name,kind,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(:id,:code,'PLB8','BUSINESS','ENABLED',now(),:id,now(),:id,1)
                """).param("id", id).param("code", "t" + id.toString().replace("-", "").substring(0, 12)).update();
        return id;
    }

    private void asTenant(UUID tenant) {
        UUID person = UUID.randomUUID();
        AccessContextHolder.set(marketIsolation.kernel().tenants().issue(new AuthenticatedMembership(
                Actor.person(person.toString()), tenant.toString(), tenant.toString(), Set.of(tenant.toString())), "plb8-trace"));
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, null, person, "plb8",
                Instant.now(), Instant.now().plusSeconds(600)));
        IsolationContextHolder.clear();
    }

    private void asPlatformOperator() {
        AccessContextHolder.clear();
        UUID home = UUID.randomUUID(), person = UUID.randomUUID();
        var actor = new one.nexus.p1.domain.Actor(person, home, "Platform", Set.of(PlatformDuty.SYSTEM_ADMIN));
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), home, UUID.randomUUID(), person, "plb8",
                Instant.now(), Instant.now().plusSeconds(300)));
        IsolationContextHolder.set(new IsolationSnapshot(actor,
                new PlatformContext(true, "authenticated platform content operation", actor, "plb8-trace", home), null, "plb8-trace"));
    }

    private List<String> events(UUID tenant) {
        return jdbc.sql("SELECT payload FROM platform.exec_outbox WHERE tenant_id=:t AND destination='p4.app_instance' ORDER BY created_at")
                .param("t", tenant.toString()).query(String.class).list();
    }

    private boolean holds(UUID tenant, String resource, String action) {
        return permissions.repository().listGrants(tenant).stream().anyMatch(grant -> grant.rule() != null
                && resource.equals(grant.rule().resource()) && action.equals(grant.rule().action()));
    }
}
