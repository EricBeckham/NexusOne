package one.nexus.permission.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.PolicySnapshotPoller;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G9 leftover on PostgreSQL 15: after a GRANT is cached, a durable DENY
 * without the invalidate channel still fail-closes on the next poll / decide.
 * Not Sentinel / Cluster.
 */
class JdbcG9FailoverRemainderIT {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void postgres15LostChannelDenyDoesNotSilentlyAllow() throws Exception {
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
            PermissionModule writer = PermissionModule.create(
                    policies, iam.repository(), new InMemoryPolicyInvalidation());
            PermissionModule reader = PermissionModule.create(
                    policies, iam.repository(), new InMemoryPolicyInvalidation());

            UUID tenant = UUID.fromString("15151515-1515-4515-8515-151515151517");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Failover Pg", "g9-failover-pg@example.com", "g9-failover-pg"));
            var person = iam.repository().findPersonByAccount(tenant,
                    iam.repository().findAccountByEmail("g9-failover-pg@example.com").orElseThrow().id()).orElseThrow();
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, person.accountId(), person.id(), "g9-failover-pg",
                    now, now.plus(Duration.ofHours(1))));

            var role = writer.admin().createRole("g9_self_reader", "G9 self reader", false);
            writer.admin().assign("user", person.id().toString(), role.id());
            writer.admin().grant(
                    "GRANT", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
            Decision allowed = reader.service().decide("ledger", "read");
            assertTrue(allowed.allowed());
            long cached = reader.snapshots().cachedVersion(tenant.toString());

            writer.admin().grant(
                    "DENY", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
            Long dbVersion = jdbc.queryForObject(
                    "SELECT policy_version FROM iam.perm_policy_version WHERE tenant_id = ?",
                    Long.class, tenant);
            assertTrue(dbVersion > cached);
            assertEquals(cached, reader.snapshots().cachedVersion(tenant.toString()),
                    "lost channel must leave the reader LRU on the GRANT snapshot until poll");

            assertEquals(1, new PolicySnapshotPoller(reader.snapshots(), Duration.ofSeconds(2)).tick());
            assertEquals(dbVersion, reader.snapshots().cachedVersion(tenant.toString()));

            Decision denied = reader.service().decide("ledger", "read");
            PolicySnapshot durable = policies.loadSnapshot(tenant);
            Decision engine = PermissionEngine.evaluate(
                    reader.subjects().resolve(TenantContextHolder.require()),
                    tenant.toString(),
                    "ledger",
                    "read",
                    durable);
            assertFalse(denied.allowed(), "PostgreSQL DENY after lost invalidate must not silently allow");
            assertEquals("explicit_deny", denied.reason());
            assertEquals(engine.allowed(), denied.allowed());
            assertEquals(engine.reason(), denied.reason());
            assertEquals(engine.policyVersion(), denied.policyVersion());
            assertEquals(1, durable.denies().size());
            assertEquals(1, durable.grants().size());
        }
    }
}
