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
import one.nexus.p3.instance.AppInstanceState;
import one.nexus.p3.instance.InstanceEvent;
import one.nexus.p3.instance.InstanceEvents;
import one.nexus.p4.market.AppInstanceService;
import one.nexus.p4.market.AppInstanceService.AppInstance;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketModels.ActivationApplicationStatus;
import one.nexus.p4.market.MarketService;
import one.nexus.permission.PermissionModule;
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

/** PLB7: product subscription → app instances + role packs + C4 events / state, on disposable PostgreSQL. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb7AppInstanceInstallTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired AppInstanceService instances;
    @Autowired AppInstanceQuery query;
    @Autowired MarketService market;
    @Autowired MarketIsolation marketIsolation;
    @Autowired PermissionModule permissions;

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

    String s;               // per-test suffix for unique codes
    String leaveCode, expenseCode;
    UUID leaveApp, expenseApp;

    @BeforeEach
    void seed() {
        s = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        leaveCode = "lv" + s;
        expenseCode = "ex" + s;
        leaveApp = app(leaveCode, "fn." + leaveCode + ".apply");
        expenseApp = app(expenseCode, "fn." + expenseCode + ".apply");
        product("office" + s, "office_" + s, leaveApp, expenseApp);
        product("leaveonly" + s, "leave_" + s, leaveApp);
    }

    @AfterEach
    void unbind() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
        IsolationContextHolder.clear();
    }

    // ---- fixtures -------------------------------------------------------------------------------------------

    UUID tenant() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO platform.tenant(id,code,name,kind,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(:id,:code,'PLB7','BUSINESS','ENABLED',now(),:id,now(),:id,1)
                """).param("id", id).param("code", "t" + id.toString().replace("-", "").substring(0, 12)).update();
        return id;
    }

    /** A published app whose manifest has one FILL entry and one role granting {@code grantResource}. */
    UUID app(String code, String grantResource) {
        UUID app = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES(:id,:code,:code,now())")
                .param("id", app).param("code", code).update();
        String manifest = """
                {"schema":"nexus.app-manifest.v1","app_code":"%1$s","version":"1.0.0","ledgers":[],"forms":[],"processes":[],
                 "role_pack":[{"code":"applicant","name":"申请人","grants":[{"resource":"%2$s","action":"read","scope":"SELF"}]}],
                 "entries":[{"key":"fn:%1$s:apply","app_code":"%1$s","form_code":"apply","kind":"FILL"}]}
                """.formatted(code, grantResource);
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at,manifest)
                VALUES(:id,:app,'1.0.0','PUBLISHED',now(),now(),CAST(:manifest AS jsonb))
                """).param("id", version).param("app", app).param("manifest", manifest).update();
        return app;
    }

    void product(String code, String sku, UUID... apps) {
        UUID product = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO market.product(id,code,name,status,created_at,updated_at,version)
                VALUES(:id,:code,:code,'PUBLISHED',now(),now(),1)
                """).param("id", product).param("code", code).update();
        int order = 0;
        for (UUID app : apps) {
            jdbc.sql("""
                    INSERT INTO market.product_app(product_id,app_id,app_version_id,sort_order)
                    SELECT :product,:app,v.id,:order FROM market.app_version v WHERE v.app_id=:app
                    """).param("product", product).param("app", app).param("order", order++).update();
        }
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,product_id,name,entitlements)
                VALUES(:code,NULL,:product,:code,'{"apps":{"enabled":true,"quota":5,"attributes":{}}}'::jsonb)
                """).param("code", sku).param("product", product).update();
    }

    void asTenant(UUID tenant) {
        UUID person = UUID.randomUUID();
        AccessContextHolder.set(marketIsolation.kernel().tenants().issue(new AuthenticatedMembership(
                Actor.person(person.toString()), tenant.toString(), tenant.toString(), Set.of(tenant.toString())), "plb7-trace"));
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, null, person, "plb7",
                Instant.now(), Instant.now().plusSeconds(600)));
    }

    void asPlatformOperator() {
        AccessContextHolder.clear();
        UUID home = UUID.randomUUID(), person = UUID.randomUUID();
        var actor = new one.nexus.p1.domain.Actor(person, home, "Platform", Set.of(PlatformDuty.SYSTEM_ADMIN));
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), home, UUID.randomUUID(), person, "plb7",
                Instant.now(), Instant.now().plusSeconds(300)));
        IsolationContextHolder.set(new IsolationSnapshot(actor,
                new PlatformContext(true, "authenticated platform content operation", actor, "plb7-trace", home), null, "plb7-trace"));
    }

    int count(String table, UUID tenant) {
        return jdbc.sql("SELECT count(*) FROM market." + table + " WHERE tenant_id=:t").param("t", tenant)
                .query(Integer.class).single();
    }

    List<String> events(UUID tenant) {
        return jdbc.sql("SELECT payload FROM platform.exec_outbox WHERE tenant_id=:t AND destination='p4.app_instance' ORDER BY created_at")
                .param("t", tenant.toString()).query(String.class).list();
    }

    boolean roleExists(UUID tenant, String code) {
        return permissions.repository().findRoleByCode(tenant, code).isPresent();
    }

    boolean roleBoundToAnyone(UUID tenant, String code) {
        return permissions.repository().listAssignments(tenant).stream().anyMatch(item -> code.equals(item.roleCode()));
    }

    // ---- tests ----------------------------------------------------------------------------------------------

    @Test
    void tenantSelfServiceInstallsEveryAppLandsUnboundRolePacksAndEmitsOneEventPerInstance() {
        UUID tenant = tenant();
        asTenant(tenant);

        List<AppInstance> installed = instances.subscribe(tenant, "office_" + s);

        assertEquals(2, installed.size());
        for (AppInstance instance : installed) {
            assertEquals("INSTALLED", instance.status());
            assertEquals("INHERIT", instance.mode());
            assertEquals(1, instance.revision());
            assertEquals(List.of("fn:" + instance.appCode() + ":apply"), instance.entries());
        }
        assertEquals(1, count("subscription", tenant));
        assertTrue(roleExists(tenant, "app_" + leaveCode + "_applicant"));
        assertTrue(roleExists(tenant, "app_" + expenseCode + "_applicant"));
        assertTrue(!roleBoundToAnyone(tenant, "app_" + leaveCode + "_applicant"), "D4: packs are not bound to positions");

        List<String> payloads = events(tenant);
        assertEquals(2, payloads.size());
        InstanceEvent event = InstanceEvents.parse(payloads.getFirst()); // C's own parser accepts B's payload
        assertEquals(1, event.revision());
        assertEquals(InstanceEvent.Kind.INSTALLED, event.kind());
        assertEquals("office" + s, event.productCode());

        List<AppInstanceState> states = query.current();
        assertEquals(2, states.size());
        assertTrue(states.stream().allMatch(AppInstanceState::installed));
        assertEquals(installed.getFirst().id(), query.current(installed.getFirst().id()).orElseThrow().instanceId());

        assertThrows(RuntimeException.class, () -> instances.subscribe(tenant, "office_" + s), "second active subscription");
    }

    @Test
    void anAlreadyInstalledAppIsReusedNotInstalledTwice() {
        UUID tenant = tenant();
        asTenant(tenant);
        instances.subscribe(tenant, "office_" + s);

        List<AppInstance> again = instances.subscribe(tenant, "leave_" + s);

        assertEquals(1, again.size());
        assertTrue(again.getFirst().reused());
        assertEquals(2, count("app_instance", tenant), "no new instance row");
        assertEquals(2, events(tenant).size(), "no event for a reused instance");
    }

    @Test
    void theQueryOnlySeesTheBoundTenantAndRefusesWithoutContext() {
        UUID first = tenant(), second = tenant();
        asTenant(first);
        instances.subscribe(first, "office_" + s);

        asTenant(second);
        assertTrue(query.current().isEmpty());
        unbind();
        assertThrows(RuntimeException.class, () -> query.current());
    }

    @Test
    void platformApprovalInstallsIntoTheApplicantTenantWithoutEvents() {
        UUID tenant = tenant();
        UUID application = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO market.activation_application(id,tenant_id,sku_code,version_id,status,created_at,created_by)
                VALUES(:id,:tenant,:sku,NULL,'PENDING',now(),:tenant)
                """).param("id", application).param("tenant", tenant).param("sku", "office_" + s).update();
        asPlatformOperator();

        var approved = market.approveActivation(application, "同意");

        assertEquals(ActivationApplicationStatus.APPROVED, approved.status());
        assertNull(approved.resultingInstallationId(), "product SKUs install app instances, not a legacy installation");
        assertEquals(2, count("app_instance", tenant));
        assertEquals(1, count("subscription", tenant));
        assertTrue(roleExists(tenant, "app_" + leaveCode + "_applicant"));
        assertTrue(events(tenant).isEmpty(), "C4 r3: the platform path emits no outbox rows");
    }

    @Test
    void aFailingRolePackRollsTheMarketSideBack() {
        UUID bad = app("bd" + s, "permission.role"); // a pack may not grant admin capabilities
        product("broken" + s, "broken_" + s, leaveApp, bad);
        UUID tenant = tenant();
        asTenant(tenant);

        assertThrows(RuntimeException.class, () -> instances.subscribe(tenant, "broken_" + s));

        assertEquals(0, count("subscription", tenant));
        assertEquals(0, count("app_instance", tenant));
        assertTrue(events(tenant).isEmpty());
    }

    @Test
    void legacySingleAppSkusAreNotHandledHere() {
        UUID legacyApp = app("lg" + s, "fn.lg" + s + ".apply");
        jdbc.sql("INSERT INTO market.sku(code,app_id,name,entitlements) VALUES(:c,:a,'旧','{}'::jsonb)")
                .param("c", "legacy_" + s).param("a", legacyApp).update();
        assertThrows(RuntimeException.class, () -> instances.plan("legacy_" + s));
    }
}
