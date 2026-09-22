package one.nexus.p2;

import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional PostgreSQL persistence check. Skips when the database is unreachable.
 * This environment used PostgreSQL 16; product baseline remains PostgreSQL 15 — not a Profile acceptance.
 */
class JdbcDualTenantIsolationTest {
    private IdentityModule module;

    @BeforeEach
    void setUp() {
        String url = System.getenv().getOrDefault(
                "NEXUS_IAM_TEST_JDBC_URL", "jdbc:postgresql:///nexus_p2_iam_test");
        String user = System.getenv().getOrDefault("NEXUS_IAM_TEST_JDBC_USER", "ubuntu");
        String password = System.getenv().getOrDefault("NEXUS_IAM_TEST_JDBC_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
        } catch (Exception ex) {
            Assumptions.abort("PostgreSQL is not available for JDBC isolation: " + ex.getMessage());
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE iam_issued_context, iam_employment, iam_position, iam_department, iam_person, iam_tenant_membership, iam_global_account CASCADE");
        StubIdentityProvider idp = new StubIdentityProvider();
        module = new IdentityModule(
                new JdbcIdentityRepository(jdbc),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8));
    }

    @AfterEach
    void tearDown() {
        one.nexus.p2.kernel.TenantContextHolder.clear();
    }

    @Test
    void personsDoNotCrossTenantOnPostgres() {
        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Admin A", "pg-admin-a@example.com", "pg-admin-a"));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantB, "Admin B", "pg-admin-b@example.com", "pg-admin-b"));

        String token = StubIdentityProvider.token("pg-alice", "pg-alice@example.com", "pg-alice", "Pg Alice");
        module.sessions().loginWithAccessToken(token);
        var alice = module.repository().findAccountByEmail("pg-alice@example.com").orElseThrow();

        var adminA = module.repository().findPersonByAccount(tenantA,
                module.repository().findAccountByEmail("pg-admin-a@example.com").orElseThrow().id()).orElseThrow();
        bind(tenantA, adminA.accountId(), adminA.id());
        module.memberships().add(alice.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Pg Alice A", "PG-A", "pg-alice@example.com", null, null, alice.id()));

        var adminB = module.repository().findPersonByAccount(tenantB,
                module.repository().findAccountByEmail("pg-admin-b@example.com").orElseThrow().id()).orElseThrow();
        bind(tenantB, adminB.accountId(), adminB.id());
        module.memberships().add(alice.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Pg Alice B", "PG-B", "pg-alice@example.com", null, null, alice.id()));

        SessionService.SessionView inA = module.sessions().switchTenant(module.sessions().authenticate(token), tenantA);
        assertEquals(tenantA, inA.tenantId());
        assertEquals(1, module.organization().listPersons().stream().filter(p -> "Pg Alice A".equals(p.displayName())).count());
        assertTrue(module.organization().listPersons().stream().noneMatch(p -> "Pg Alice B".equals(p.displayName())));

        module.sessions().switchTenant(module.sessions().authenticate(token), tenantB);
        assertTrue(module.organization().listPersons().stream().noneMatch(p -> "Pg Alice A".equals(p.displayName())));
        assertEquals(1, module.organization().listPersons().stream().filter(p -> "Pg Alice B".equals(p.displayName())).count());
    }

    private static void bind(UUID tenantId, UUID accountId, UUID personId) {
        java.time.Instant now = java.time.Instant.now();
        one.nexus.p2.kernel.TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(
                UUID.randomUUID(), tenantId, accountId, personId, "pg", now, now.plus(Duration.ofHours(1))));
    }
}
