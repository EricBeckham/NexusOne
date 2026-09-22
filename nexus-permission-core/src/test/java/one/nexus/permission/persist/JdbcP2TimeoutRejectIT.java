package one.nexus.permission.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.identity.IamSubjectDirectory;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G9 leftover on PostgreSQL 15: {@code decide} rejects after the configured
 * SnapshotManager clock when {@code iam.perm_policy_version} load hangs
 * ({@code pg_sleep}). Healthy JDBC load is unchanged. Not Sentinel / Cluster.
 */
class JdbcP2TimeoutRejectIT {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void postgres15DecideRejectsWhenVersionLoadHangsPastTimeout() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
            try (Connection connection = postgres.getPostgresDatabase().getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SHOW server_version")) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).startsWith("15."), "expected PostgreSQL 15, got " + rs.getString(1));
            }
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setURL("jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
            ds.setUser("postgres");
            ds.setPassword("postgres");
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .schemas("iam")
                    .defaultSchema("iam")
                    .createSchemas(true)
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    new JdbcIdentityRepository(jdbc),
                    idp,
                    new KeycloakSmtpHook(idp, false),
                    Clock.systemUTC(),
                    Duration.ofHours(8));
            JdbcPolicyRepository policies = new JdbcPolicyRepository(jdbc);
            PermissionModule module = PermissionModule.create(
                    policies, iam.repository(), new InMemoryPolicyInvalidation());

            UUID tenant = UUID.fromString("15151515-1515-4515-8515-151515151516");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Timeout Pg", "g9-timeout-pg@example.com", "g9-timeout-pg"));
            var person = iam.repository().findPersonByAccount(tenant,
                    iam.repository().findAccountByEmail("g9-timeout-pg@example.com").orElseThrow().id()).orElseThrow();
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, person.accountId(), person.id(), "g9-timeout-pg",
                    now, now.plus(Duration.ofHours(1))));

            var role = module.admin().createRole("g9_timeout_reader", "G9 timeout reader", false);
            module.admin().assign("user", person.id().toString(), role.id());
            module.admin().grant(
                    "GRANT", "role", "g9_timeout_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());

            Decision healthy = module.service().decide("ledger", "read");
            PolicySnapshot durable = policies.loadSnapshot(tenant);
            Decision engine = PermissionEngine.evaluate(
                    module.subjects().resolve(TenantContextHolder.require()),
                    tenant.toString(),
                    "ledger",
                    "read",
                    durable);
            assertTrue(healthy.allowed());
            assertEquals("allowed", healthy.reason());
            assertEquals(engine.allowed(), healthy.allowed());
            assertEquals(engine.reason(), healthy.reason());
            assertEquals(engine.policyVersion(), healthy.policyVersion());
            assertEquals(SnapshotManager.DEFAULT_LOAD_TIMEOUT, module.snapshots().loadTimeout());

            Duration timeout = Duration.ofMillis(200);
            SnapshotManager hanging = new SnapshotManager(
                    id -> {
                        jdbc.execute("SELECT pg_sleep(3)");
                        return policies.currentVersion(UUID.fromString(id));
                    },
                    id -> policies.loadSnapshot(UUID.fromString(id)),
                    timeout);
            PermissionService hung = new PermissionService(
                    policies, new IamSubjectDirectory(iam.repository(), policies), hanging);

            long started = System.nanoTime();
            PermissionException ex = assertThrows(PermissionException.class, () -> hung.decide("ledger", "read"));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertEquals("P2.PERM.0005", ex.errorCode().code());
            assertEquals(503, ex.httpStatus());
            assertTrue(ex.getMessage().contains("timed out after 200ms"), ex.getMessage());
            assertTrue(elapsedMs >= 150, "elapsed=" + elapsedMs);
            assertTrue(elapsedMs < 2_500, "must reject near the timeout, not wait for pg_sleep; elapsed=" + elapsedMs);

            Decision after = module.service().decide("ledger", "read");
            assertTrue(after.allowed());
            assertEquals(healthy.policyVersion(), after.policyVersion());
            assertFalse(module.snapshots().isMarkedInvalidated(tenant.toString()));
        }
    }
}
