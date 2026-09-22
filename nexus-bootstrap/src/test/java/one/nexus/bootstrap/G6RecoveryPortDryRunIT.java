package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import one.nexus.audit.recovery.DryRunRecoveryPort;
import one.nexus.audit.recovery.RecoveryDryRunResult;
import one.nexus.audit.recovery.RecoveryPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Product-wired {@link RecoveryPort} stays {@link DryRunRecoveryPort}. Dry-run
 * must not mutate the product-shaped PostgreSQL 15 stand-in. Never
 * {@code haopan_platform}, never port {@code 5432}.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class G6RecoveryPortDryRunIT {
    private static EmbeddedPostgres postgres;

    @Autowired
    RecoveryPort recoveryPort;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            if (!rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + rs.getString(1));
            }
        }
        assertNotEquals(5432, postgres.getPort(), "product stand-in must not use port 5432");
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("nexus.notify.outbox", () -> "auto");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void productBeanIsDryRunAndDoesNotMutateStandInDb() {
        assertInstanceOf(DryRunRecoveryPort.class, recoveryPort);
        assertNotNull(jdbc.getDataSource());
        assertFalse(jdbc.queryForObject("select current_database()", String.class).contains("haopan"));
        List<String> haopan = jdbc.queryForList(
                "select datname from pg_database where datname ilike '%haopan%'", String.class);
        assertTrue(haopan.isEmpty(), "dry-run contract must not create haopan_platform, got " + haopan);

        ProductSnapshot before = snapshot();
        RecoveryDryRunResult pg = recoveryPort.dryRunPostgres();
        RecoveryDryRunResult objects = recoveryPort.dryRunObjectStore();
        ProductSnapshot after = snapshot();

        assertEquals(before, after, "dry-run must not mutate the product-shaped stand-in database");
        assertFalse(pg.executed());
        assertFalse(pg.profileAccepted());
        assertFalse(objects.executed());
        assertFalse(objects.profileAccepted());
        assertTrue("DRY_RUN".equals(pg.status()) || "TOOL_MISSING".equals(pg.status()));
        assertEquals("DRY_RUN", objects.status());
        assertTrue(jdbc.queryForList("select datname from pg_database where datname ilike '%haopan%'", String.class)
                .isEmpty());
    }

    private ProductSnapshot snapshot() {
        Integer flywayRows = jdbc.queryForObject(
                "select count(*) from platform.flyway_schema_history where success", Integer.class);
        String flywayHash = jdbc.queryForObject(
                "select md5(string_agg(version || ':' || description, ',' order by installed_rank)) "
                        + "from platform.flyway_schema_history where success and version is not null",
                String.class);
        Integer auditRows = jdbc.queryForObject("select count(*) from platform.audit_record", Integer.class);
        String chainHead = jdbc.queryForObject(
                "select next_index || ':' || encode(last_hash, 'hex') from platform.audit_chain_meta",
                String.class);
        List<String> databases = jdbc.queryForList("select datname from pg_database order by 1", String.class);
        return new ProductSnapshot(flywayRows, flywayHash, auditRows, chainHead, databases);
    }

    private record ProductSnapshot(
            Integer flywayRows, String flywayHash, Integer auditRows, String chainHead, List<String> databases) {}
}
