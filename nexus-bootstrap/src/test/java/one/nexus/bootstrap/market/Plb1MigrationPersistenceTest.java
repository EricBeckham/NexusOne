package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** PLB1 schema migration against a disposable PostgreSQL 15 instance, never a configured business database. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb1MigrationPersistenceTest {
    private static EmbeddedPostgres postgres;

    @Autowired JdbcClient jdbc;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.demo.enabled", () -> "false");
    }

    @Test
    void legacyVersionsRemainReadableAndNewManifestIsConstrained() {
        UUID app = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO market.app_package (id, code, name, created_at)
                VALUES (:id, 'plb1_test', 'PLB1 test app', now())
                """).param("id", app).update();
        jdbc.sql("""
                INSERT INTO market.app_version (id, app_id, version, status, created_at)
                VALUES (:id, :app, '1.0.0', 'DRAFT', now())
                """).param("id", version).param("app", app).update();
        assertEquals(1, jdbc.sql("SELECT count(*) FROM market.app_version WHERE id = :id AND manifest IS NULL")
                .param("id", version).query(Integer.class).single());

        jdbc.sql("""
                INSERT INTO market.app_category (code, name, created_at, updated_at)
                VALUES ('oa', '办公', now(), now())
                """).update();
        jdbc.sql("UPDATE market.app_package SET category_code = 'oa' WHERE id = :id")
                .param("id", app).update();
        jdbc.sql("UPDATE market.app_version SET manifest = CAST(:manifest AS jsonb) WHERE id = :id")
                .param("manifest", "{\"schema\":\"nexus.app-manifest.v1\",\"app_code\":\"plb1_test\"}")
                .param("id", version).update();
        assertEquals("oa", jdbc.sql("SELECT category_code FROM market.app_package WHERE id = :id")
                .param("id", app).query(String.class).single());
        assertEquals("nexus.app-manifest.v1", jdbc.sql("SELECT manifest ->> 'schema' FROM market.app_version WHERE id = :id")
                .param("id", version).query(String.class).single());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO market.app_category (code, name, created_at, updated_at)
                VALUES ('Bad-Code', 'Invalid', now(), now())
                """).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                UPDATE market.app_version SET manifest = '{"schema":"unsupported"}'::jsonb WHERE id = :id
                """).param("id", version).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("DELETE FROM market.app_category WHERE code = 'oa'")
                .update());
    }
}
