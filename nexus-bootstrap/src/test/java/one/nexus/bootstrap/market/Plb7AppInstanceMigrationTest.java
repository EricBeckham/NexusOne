package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PLB7a: V716 market.app_instance constraints and nullable activation version, on disposable PostgreSQL. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb7AppInstanceMigrationTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;

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

    UUID tenant, product, app, version, subscription;

    void seed() {
        tenant = UUID.randomUUID(); product = UUID.randomUUID(); app = UUID.randomUUID();
        version = UUID.randomUUID(); subscription = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        String suffix = tenant.toString().substring(0, 8);
        jdbc.sql("""
                INSERT INTO platform.tenant(id,code,name,kind,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(:id,:code,'PLB7 租户','BUSINESS','ENABLED',now(),:actor,now(),:actor,1)
                """).param("id", tenant).param("code", "plb7_" + suffix).param("actor", actor).update();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES(:id,:code,'请假',now())")
                .param("id", app).param("code", "lv" + suffix).update();
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at,manifest)
                VALUES(:id,:app,'1.0.0','PUBLISHED',now(),now(),'{"schema":"nexus.app-manifest.v1"}'::jsonb)
                """).param("id", version).param("app", app).update();
        jdbc.sql("""
                INSERT INTO market.product(id,code,name,status,created_at,updated_at,version)
                VALUES(:id,:code,'办公套件','PUBLISHED',now(),now(),1)
                """).param("id", product).param("code", "p" + suffix).update();
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,product_id,name,entitlements)
                VALUES(:code,NULL,:product,'标准版','{}'::jsonb)
                """).param("code", "sku_" + suffix).param("product", product).update();
        jdbc.sql("""
                INSERT INTO market.subscription(id,tenant_id,sku_code,status,started_at)
                VALUES(:id,:tenant,:sku,'ACTIVE',now())
                """).param("id", subscription).param("tenant", tenant).param("sku", "sku_" + suffix).update();
    }

    int insertInstance(UUID id, String status, String mode, String entries, long revision) {
        return jdbc.sql("""
                INSERT INTO market.app_instance(id,tenant_id,product_id,sku_code,subscription_id,app_id,app_version_id,
                    status,mode,entries,revision,created_at,changed_at)
                VALUES(:id,:tenant,:product,:sku,:subscription,:app,:version,:status,:mode,CAST(:entries AS jsonb),:revision,now(),now())
                """).param("id", id).param("tenant", tenant).param("product", product)
                .param("sku", "sku_" + tenant.toString().substring(0, 8)).param("subscription", subscription)
                .param("app", app).param("version", version).param("status", status).param("mode", mode)
                .param("entries", entries).param("revision", revision).update();
    }

    @Test
    void anInstalledInstanceKeepsItsStateAndOnlyOnePerAppIsActive() {
        seed();
        UUID first = UUID.randomUUID();
        assertEquals(1, insertInstance(first, "INSTALLED", "INHERIT", "[\"fn:leave:leave_apply\"]", 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "INSTALLED", "INHERIT", "[]", 1), "one installed instance per app");

        jdbc.sql("UPDATE market.app_instance SET status='UNINSTALLED',mode=NULL,entries='[]'::jsonb,revision=2 WHERE id=:id")
                .param("id", first).update();
        assertEquals(1, insertInstance(UUID.randomUUID(), "INSTALLED", "INHERIT", "[]", 1),
                "after uninstall the app can be installed again; the old row stays");
        assertEquals(2, jdbc.sql("SELECT count(*) FROM market.app_instance WHERE tenant_id=:t").param("t", tenant)
                .query(Integer.class).single());
    }

    @Test
    void stateConstraintsHold() {
        seed();
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "INSTALLED", null, "[]", 1), "installed needs a mode");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "UNINSTALLED", "INHERIT", "[]", 1), "uninstalled has no mode");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "PAUSED", "INHERIT", "[]", 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "INSTALLED", "INHERIT", "{}", 1), "entries is an array");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstance(UUID.randomUUID(), "INSTALLED", "INHERIT", "[]", 0), "revision starts at 1");
    }

    @Test
    void activationRequestsForProductSkusMayOmitTheVersion() {
        assertTrue(jdbc.sql("""
                SELECT is_nullable='YES' FROM information_schema.columns
                WHERE table_schema='market' AND table_name='activation_application' AND column_name='version_id'
                """).query(Boolean.class).single());
    }
}
