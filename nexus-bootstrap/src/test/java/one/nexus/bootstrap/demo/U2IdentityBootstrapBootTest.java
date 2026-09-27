package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import one.nexus.bootstrap.NexusOneApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

/** Full startup with real IAM/permission JDBC, demo mode and P1 demo context both closed. */
@SpringBootTest(classes = NexusOneApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class U2IdentityBootstrapBootTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformDutyBootstrapRunner runner;
    @Autowired one.nexus.permission.PermissionModule permissions;

    @BeforeAll
    static void open() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void close() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        // Disposable embedded test DB uses trust authentication; no password supplied.
        registry.add("spring.datasource.password", () -> "");
        registry.add("nexus.iam.persistence", () -> "jdbc");
        registry.add("nexus.permission.persistence", () -> "jdbc");
        for (String module : java.util.List.of("iam", "permission")) {
            registry.add("nexus." + module + ".jdbc.url",
                    () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
            registry.add("nexus." + module + ".jdbc.username", () -> "postgres");
            registry.add("nexus." + module + ".jdbc.password", () -> "");
            registry.add("nexus." + module + ".jdbc.migrate", () -> "false");
        }
        registry.add("nexus.demo.enabled", () -> "false");
        registry.add("nexus.p1.demo-context.enabled", () -> "false");
        registry.add(PlatformDutyBootstrap.ENABLED, () -> "true");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void startupAndRepeatedWindowHaveThreePacksAndNoDanglingSystemAdminAssignment() {
        assertRows();
        runner.run(new DefaultApplicationArguments());
        assertRows();
    }

    private void assertRows() {
        assertInstanceOf(one.nexus.permission.persist.JdbcPolicyRepository.class, permissions.repository());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM iam.iam_global_account", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM iam.iam_person", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM iam.iam_tenant_membership WHERE status = 'ACTIVE' AND deleted_at IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM iam.perm_assignment a JOIN iam.perm_role r ON r.id = a.role_id
                JOIN iam.iam_person p ON p.id::text = a.principal_id AND p.tenant_id = a.tenant_id
                WHERE a.principal_type = 'user' AND a.principal_id = ? AND r.code = 'platform_system_admin'
                  AND a.deleted_at IS NULL AND p.deleted_at IS NULL
                """, Integer.class, P2DemoSeedConfiguration.PERSON_PLATFORM_ADMIN.toString()),
                () -> jdbc.queryForList("SELECT principal_type, principal_id, r.code FROM iam.perm_assignment a "
                        + "JOIN iam.perm_role r ON r.id = a.role_id").toString());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM iam.perm_role WHERE code = 'demo_operator'", Integer.class));
    }
}
