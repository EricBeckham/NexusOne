package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

/** ID1 + ID3: real disposable PostgreSQL, no external database or IdP. */
class U2IdentityBootstrapPostgresTest {
    private EmbeddedPostgres postgres;
    private JdbcTemplate jdbc;
    private PermissionModule permissions;
    private PlatformDutyBootstrapRunner runner;

    @BeforeEach
    void openEmptyDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        var datasource = postgres.getPostgresDatabase();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V200__iam_identity.sql"))
                .execute(datasource);
        jdbc = new JdbcTemplate(datasource);
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), new JdbcIdentityRepository(jdbc),
                new InMemoryPolicyInvalidation());
        // Production-shaped URL proves U2 does not depend on the local demo gate.
        var env = new MockEnvironment().withProperty(PlatformDutyBootstrap.ENABLED, "true")
                .withProperty(PlatformDutyBootstrap.DEMO_CONTEXT, "false")
                .withProperty(DemoProfileGate.ENABLED, "false")
                .withProperty("spring.datasource.url", "jdbc:postgresql://demo-host.invalid/nexusone");
        runner = new PlatformDutyBootstrapRunner(env, permissions, new P1Properties(), JdbcClient.create(datasource),
                new TransactionTemplate(new DataSourceTransactionManager(datasource)));
    }

    @AfterEach
    void closeDatabase() throws Exception {
        TenantContextHolder.clear();
        if (postgres != null) postgres.close();
    }

    @Test
    void emptyDatabaseInsertsThreeCompletePacksBeforeAssigningPlatformDutyAndRepeatsWithoutDuplicates() {
        run();
        assertPacks();
        assertEquals(1, permissions.repository().listAssignments(P2DemoSeedConfiguration.PLATFORM_TENANT_ID).size());
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM iam.iam_person WHERE id = ?)",
                Boolean.class, PlatformDutyBootstrap.PLATFORM_OPS_PERSON_ID));
        var versions = jdbc.queryForList("SELECT id, version FROM iam.iam_global_account ORDER BY id");
        run();
        assertPacks();
        assertEquals(versions, jdbc.queryForList("SELECT id, version FROM iam.iam_global_account ORDER BY id"));
        assertEquals(1, permissions.repository().listAssignments(P2DemoSeedConfiguration.PLATFORM_TENANT_ID).size());
        assertEquals(0, count("SELECT count(*) FROM iam.iam_tenant_membership WHERE status = 'SUSPENDED'"));
    }

    @Test
    void existingRowsAlignLabelsPrimaryJobsAndSingleMembershipWithoutReplacingRealIdpSubject() {
        run();
        jdbc.update("UPDATE iam.iam_global_account SET username = 'old', email = 'old@demo.invalid', "
                + "keycloak_user_id = 'idp-existing-subject' WHERE id = ?", P2DemoSeedConfiguration.ACCOUNT_LIN);
        jdbc.update("UPDATE iam.iam_position SET name = '旧岗位' WHERE id = ?", P2DemoSeedConfiguration.POSITION_TENANT_ADMIN);
        jdbc.update("UPDATE iam.iam_employment SET kind = 'SECONDARY' WHERE id = ?", P2DemoSeedConfiguration.EMPLOYMENT_LIN_ADMIN);
        jdbc.update("UPDATE iam.iam_tenant_membership SET status = 'SUSPENDED' WHERE account_id = ?",
                P2DemoSeedConfiguration.ACCOUNT_LIN);
        jdbc.update("INSERT INTO iam.iam_tenant_membership VALUES (?, ?, ?, 'ACTIVE', now(), now(), NULL, 0)",
                UUID.randomUUID(), UUID.randomUUID(), P2DemoSeedConfiguration.ACCOUNT_LIN);
        run();
        assertPacks();
        assertEquals("idp-existing-subject", jdbc.queryForObject(
                "SELECT keycloak_user_id FROM iam.iam_global_account WHERE id = ?", String.class,
                P2DemoSeedConfiguration.ACCOUNT_LIN));
        assertEquals("租户管理", jdbc.queryForObject("SELECT name FROM iam.iam_position WHERE id = ?",
                String.class, P2DemoSeedConfiguration.POSITION_TENANT_ADMIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform.admin@demo.invalid", "lin.xiao@demo.invalid", "zhou.ran@demo.invalid"})
    void orphanEmailFailsHonestlyAndDoesNotInsertOrGrantAnything(String email) {
        UUID orphan = UUID.randomUUID();
        jdbc.update("INSERT INTO iam.iam_global_account VALUES (?, 'orphan-idp', ?, NULL, 'orphan', 'Orphan', "
                + "'ACTIVE', now(), now(), NULL, 0)", orphan, email.toUpperCase(java.util.Locale.ROOT));
        var error = assertThrows(IllegalStateException.class, this::run);
        assertTrue(error.getMessage().startsWith("ID1 identity conflict"));
        assertEquals(1, count("SELECT count(*) FROM iam.iam_global_account"));
        assertEquals(0, count("SELECT count(*) FROM iam.iam_person"));
        assertTrue(permissions.repository().listAssignments(P2DemoSeedConfiguration.PLATFORM_TENANT_ID).isEmpty());
        assertTrue(permissions.repository().listRoles(P2DemoSeedConfiguration.PLATFORM_TENANT_ID).isEmpty());
    }

    @Test
    void partialInsertFailureRollsBackBeforeAnyAuthorizationIsInstalled() {
        jdbc.update("INSERT INTO iam.iam_position VALUES (?, ?, 'demo_member', 'conflict', NULL, NULL, 'ACTIVE', "
                + "now(), now(), NULL, 0)", UUID.randomUUID(), P2DemoSeedConfiguration.TENANT_ID);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, this::run);
        assertEquals(0, count("SELECT count(*) FROM iam.iam_global_account"));
        assertEquals(0, count("SELECT count(*) FROM iam.iam_person"));
        assertTrue(permissions.repository().listRoles(P2DemoSeedConfiguration.PLATFORM_TENANT_ID).isEmpty());
    }

    @Test
    void conflictingFixedPersonFailsRatherThanRebindingOwnership() {
        run();
        jdbc.update("UPDATE iam.iam_person SET tenant_id = ? WHERE id = ?", UUID.randomUUID(),
                P2DemoSeedConfiguration.PERSON_PLATFORM_ADMIN);
        assertThrows(IllegalStateException.class, this::run);
    }

    private void assertPacks() {
        assertEquals(3, count("SELECT count(*) FROM iam.iam_global_account"));
        assertEquals(3, count("SELECT count(*) FROM iam.iam_person"));
        assertEquals(3, count("SELECT count(*) FROM iam.iam_tenant_membership WHERE status = 'ACTIVE'"));
        assertEquals(3, count("SELECT count(*) FROM iam.iam_employment WHERE kind = 'PRIMARY' AND status = 'ACTIVE'"));
        assertEquals(3, count("""
                SELECT count(*) FROM iam.iam_global_account a JOIN iam.iam_person p ON p.account_id = a.id
                JOIN iam.iam_tenant_membership m ON m.account_id = a.id AND m.tenant_id = p.tenant_id
                JOIN iam.iam_employment e ON e.person_id = p.id AND e.tenant_id = p.tenant_id
                JOIN iam.iam_position pos ON pos.id = e.position_id
                WHERE m.status = 'ACTIVE' AND e.kind = 'PRIMARY' AND e.status = 'ACTIVE' AND p.email = a.email
                  AND ((a.id = '01900000-0000-7000-8000-000000000221' AND a.username = 'nexus-demo-platform'
                        AND a.email = 'platform.admin@demo.invalid' AND pos.code = 'demo_platform_ops')
                    OR (a.id = '01900000-0000-7000-8000-000000000201' AND a.username = 'nexus-demo-tenant-admin'
                        AND a.email = 'lin.xiao@demo.invalid' AND pos.code = 'demo_tenant_admin')
                    OR (a.id = '01900000-0000-7000-8000-000000000202' AND a.username = 'nexus-demo-member'
                        AND a.email = 'zhou.ran@demo.invalid' AND pos.code = 'demo_member'))
                """));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void run() {
        runner.run(new DefaultApplicationArguments());
    }
}
