package one.nexus.permission.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PostgresAvailable;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class JdbcDualTenantPermissionTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void snapshotsStayOnOwningTenantOnPostgres15() throws Exception {
        try (var connection = PostgresAvailable.openOrAbort()) {
            String version = PostgresAvailable.version(connection);
            assertTrue(version.startsWith("PostgreSQL 15."), version);
            var dataSource = new SingleConnectionDataSource(connection, true);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("iam")
                    .defaultSchema("iam")
                    .createSchemas(true)
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("""
                    TRUNCATE iam.perm_record_share, iam.perm_field_policy, iam.perm_grant,
                             iam.perm_assignment, iam.perm_role_parent, iam.perm_role,
                             iam.perm_policy_version,
                             iam.iam_issued_context, iam.iam_employment, iam.iam_position,
                             iam.iam_department, iam.iam_person, iam.iam_tenant_membership,
                             iam.iam_global_account CASCADE
                    """);
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    new JdbcIdentityRepository(jdbc),
                    idp,
                    new KeycloakSmtpHook(idp, false),
                    Clock.systemUTC(),
                    Duration.ofHours(8));
            JdbcPolicyRepository policies = new JdbcPolicyRepository(jdbc);
            PermissionModule module = PermissionModule.create(policies, iam.repository(), new InMemoryPolicyInvalidation());

            UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
            UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenantA, "Admin A", "pg-perm-a@example.com", "pg-perm-a"));
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenantB, "Admin B", "pg-perm-b@example.com", "pg-perm-b"));
            var personA = iam.repository().findPersonByAccount(tenantA,
                    iam.repository().findAccountByEmail("pg-perm-a@example.com").orElseThrow().id()).orElseThrow();
            var personB = iam.repository().findPersonByAccount(tenantB,
                    iam.repository().findAccountByEmail("pg-perm-b@example.com").orElseThrow().id()).orElseThrow();

            bind(tenantA, personA.accountId(), personA.id());
            var roleA = module.admin().createRole("employee", "Employee", false);
            module.admin().assign("user", personA.id().toString(), roleA.id());
            module.admin().grant("GRANT", "role", "employee", "ledger", "read", DataScopeKind.SELF, List.of(), java.util.Map.of());

            bind(tenantB, personB.accountId(), personB.id());
            module.admin().createRole("employee", "Employee", false);

            PolicySnapshot snapA = policies.loadSnapshot(tenantA);
            PolicySnapshot snapB = policies.loadSnapshot(tenantB);
            assertEquals(1, snapA.grants().size());
            assertTrue(snapB.grants().isEmpty());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM iam.perm_grant WHERE tenant_id = ?", Long.class, tenantB).longValue());
            Long versionA = jdbc.queryForObject(
                    "SELECT policy_version FROM iam.perm_policy_version WHERE tenant_id = ?", Long.class, tenantA);
            assertTrue(versionA >= 1);
        }
    }

    private static void bind(UUID tenantId, UUID accountId, UUID personId) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, accountId, personId, "pg", now, now.plus(Duration.ofHours(1))));
    }
}
