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
 * G9 leftover on PostgreSQL 15: two in-process readers share
 * {@code iam.perm_policy_version} but not the invalidate bus. After a
 * durable DENY the version poll on each node converges independently.
 * Not Sentinel / Cluster / replica promotion.
 */
class JdbcG9DualReaderPartitionIT {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void postgres15DualReaderLostInvalidateConvergesViaIndependentPoll() throws Exception {
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
            PermissionModule readerA = PermissionModule.create(
                    policies, iam.repository(), new InMemoryPolicyInvalidation());
            PermissionModule readerB = PermissionModule.create(
                    policies, iam.repository(), new InMemoryPolicyInvalidation());

            UUID tenant = UUID.fromString("16161616-1616-4616-8616-161616161616");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Partition Pg", "g9-partition-pg@example.com", "g9-partition-pg"));
            var person = iam.repository().findPersonByAccount(tenant,
                    iam.repository().findAccountByEmail("g9-partition-pg@example.com").orElseThrow().id())
                    .orElseThrow();
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, person.accountId(), person.id(), "g9-partition-pg",
                    now, now.plus(Duration.ofHours(1))));

            var role = writer.admin().createRole("g9_self_reader", "G9 self reader", false);
            writer.admin().assign("user", person.id().toString(), role.id());
            writer.admin().grant(
                    "GRANT", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
            Decision allowedA = readerA.service().decide("ledger", "read");
            Decision allowedB = readerB.service().decide("ledger", "read");
            assertTrue(allowedA.allowed());
            assertTrue(allowedB.allowed());
            long cachedA = readerA.snapshots().cachedVersion(tenant.toString());
            long cachedB = readerB.snapshots().cachedVersion(tenant.toString());
            assertEquals(cachedA, cachedB);

            writer.admin().grant(
                    "DENY", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
            Long dbVersion = jdbc.queryForObject(
                    "SELECT policy_version FROM iam.perm_policy_version WHERE tenant_id = ?",
                    Long.class, tenant);
            assertTrue(dbVersion > cachedA);
            assertEquals(cachedA, readerA.snapshots().cachedVersion(tenant.toString()),
                    "lost channel must leave reader A LRU on the GRANT snapshot until its own poll");
            assertEquals(cachedB, readerB.snapshots().cachedVersion(tenant.toString()),
                    "lost channel must leave reader B LRU on the GRANT snapshot until its own poll");
            assertFalse(readerA.snapshots().isMarkedInvalidated(tenant.toString()));
            assertFalse(readerB.snapshots().isMarkedInvalidated(tenant.toString()));

            assertEquals(1, new PolicySnapshotPoller(readerA.snapshots(), Duration.ofSeconds(2)).tick());
            assertEquals(dbVersion, readerA.snapshots().cachedVersion(tenant.toString()));
            assertEquals(cachedB, readerB.snapshots().cachedVersion(tenant.toString()),
                    "reader A's poll must not mutate reader B's LRU");

            assertEquals(1, new PolicySnapshotPoller(readerB.snapshots(), Duration.ofSeconds(2)).tick());
            assertEquals(dbVersion, readerB.snapshots().cachedVersion(tenant.toString()));

            Decision deniedA = readerA.service().decide("ledger", "read");
            Decision deniedB = readerB.service().decide("ledger", "read");
            PolicySnapshot durable = policies.loadSnapshot(tenant);
            Decision engine = PermissionEngine.evaluate(
                    readerA.subjects().resolve(TenantContextHolder.require()),
                    tenant.toString(),
                    "ledger",
                    "read",
                    durable);
            assertFalse(deniedA.allowed(), "PostgreSQL partitioned reader A must not silently allow");
            assertFalse(deniedB.allowed(), "PostgreSQL partitioned reader B must not silently allow");
            assertEquals("explicit_deny", deniedA.reason());
            assertEquals("explicit_deny", deniedB.reason());
            assertEquals(engine.allowed(), deniedA.allowed());
            assertEquals(engine.reason(), deniedA.reason());
            assertEquals(engine.policyVersion(), deniedA.policyVersion());
            assertEquals(engine.allowed(), deniedB.allowed());
            assertEquals(engine.reason(), deniedB.reason());
            assertEquals(engine.policyVersion(), deniedB.policyVersion());
            assertEquals(1, durable.denies().size());
            assertEquals(1, durable.grants().size());
        }
    }
}
