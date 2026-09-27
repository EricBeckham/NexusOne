package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Never use an external/default JDBC URL: every row belongs to this disposable random-port cluster. */
class LogoutContextPostgresTest {
    @Test
    void jdbcLogoutOnlyRevokesTheOwnedCurrentContextAndRemainsIdempotent() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().start()) {
            String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
            var datasource = new DriverManagerDataSource(url, "postgres", "");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V200__iam_identity.sql"))
                    .execute(datasource);
            var jdbc = new JdbcTemplate(datasource);
            var repository = new JdbcIdentityRepository(jdbc);
            var idp = new StubIdentityProvider();
            var module = new IdentityModule(repository, idp, new KeycloakSmtpHook(idp, false),
                    Clock.systemUTC(), Duration.ofHours(8));
            UUID tenant = UUID.randomUUID();
            module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "Isolation test", "logout-isolation@example.invalid", "logout-isolation"));
            var a = module.sessions().loginWithCode("stub-code", "http://localhost/callback");
            var b = module.sessions().loginWithCode("stub-code", "http://localhost/callback");
            var identity = module.sessions().authenticate(a.accessToken());

            repository.revokeContext(a.contextId(), UUID.randomUUID());
            assertNull(repository.findContext(a.contextId()).orElseThrow().revokedAt());
            module.sessions().loadContext(a.contextId(), identity);
            module.sessions().logout(identity, null, null);
            var revoked = repository.findContext(a.contextId()).orElseThrow().revokedAt();
            assertNotNull(revoked);
            assertThrows(DomainException.class, () -> module.sessions().loadContext(a.contextId(), identity));
            assertEquals(b.contextId(), module.sessions().loadContext(b.contextId(), identity).contextId());
            assertNull(repository.findContext(b.contextId()).orElseThrow().revokedAt());
            repository.revokeContext(a.contextId(), a.accountId());
            assertEquals(revoked, repository.findContext(a.contextId()).orElseThrow().revokedAt());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM iam.iam_issued_context WHERE revoked_at IS NOT NULL", Integer.class));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
