package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.G9SentinelClusterHonesty;
import one.nexus.permission.redis.G9SentinelClusterHonesty.Inventory;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.redis.Redis32Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #128: honesty inventory for Sentinel / Cluster /
 * replica promotion / live multi-JVM. Reuses the same
 * {@code PermissionModule.create} + shared {@code InMemoryPolicyRepository}
 * as the in-process dual-reader harness. Does not invent a second snapshot
 * store. Does not claim product G9 close.
 */
class G9SentinelClusterHonestyTest {
    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void sameSnapshotStoreStillFailClosesWithoutSentinel() {
        InMemoryPolicyInvalidation writerBus = new InMemoryPolicyInvalidation();
        InMemoryPolicyInvalidation readerABus = new InMemoryPolicyInvalidation();
        InMemoryPolicyInvalidation readerBBus = new InMemoryPolicyInvalidation();
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
        PermissionModule writer = PermissionModule.create(policies, identity, writerBus);
        PermissionModule readerA = PermissionModule.create(policies, identity, readerABus);
        PermissionModule readerB = PermissionModule.create(policies, identity, readerBBus);

        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule iam = new IdentityModule(
                identity, idp, new KeycloakSmtpHook(idp, false),
                java.time.Clock.systemUTC(), Duration.ofHours(1));
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G9 Honesty Admin", "g9-honesty@example.com", "g9-honesty"));
        var person = identity.findPersonByAccount(TENANT,
                identity.findAccountByEmail("g9-honesty@example.com").orElseThrow().id()).orElseThrow();
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, person.accountId(), person.id(), "g9-honesty",
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
        long cached = readerA.snapshots().cachedVersion(TENANT.toString());
        assertEquals(cached, readerB.snapshots().cachedVersion(TENANT.toString()));

        writer.admin().grant(
                "DENY", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        assertEquals(cached, readerA.snapshots().cachedVersion(TENANT.toString()));
        assertEquals(cached, readerB.snapshots().cachedVersion(TENANT.toString()));
        assertFalse(readerABus.wasNotified(TENANT.toString()));
        assertFalse(readerBBus.wasNotified(TENANT.toString()));

        assertEquals(1, new PolicySnapshotPoller(readerA.snapshots(), Duration.ZERO).tick());
        assertEquals(cached, readerB.snapshots().cachedVersion(TENANT.toString()),
                "reader A's poll must not mutate reader B; same #128 store");
        assertEquals(1, new PolicySnapshotPoller(readerB.snapshots(), Duration.ZERO).tick());

        Decision deniedA = readerA.service().decide("ledger", "read");
        Decision deniedB = readerB.service().decide("ledger", "read");
        PolicySnapshot durable = policies.loadSnapshot(TENANT);
        Decision engine = PermissionEngine.evaluate(
                readerA.subjects().resolve(TenantContextHolder.require()),
                TENANT.toString(),
                "ledger",
                "read",
                durable);
        assertFalse(deniedA.allowed());
        assertFalse(deniedB.allowed());
        assertEquals("explicit_deny", deniedA.reason());
        assertEquals(engine.reason(), deniedA.reason());
        assertEquals(engine.policyVersion(), deniedB.policyVersion());
    }

    @Test
    void liveRedisInventoryIsStandaloneOrMissingAndCannotClaimFailover() {
        Inventory inventory = G9SentinelClusterHonesty.probeLive();
        assertEquals("SKIP", inventory.sentinelFailover());
        assertEquals("SKIP", inventory.clusterFailover());
        assertEquals("SKIP", inventory.replicaPromotion());
        assertEquals("SKIP", inventory.liveMultiJvm());
        assertFalse(inventory.skipReason().isBlank());
        if (inventory.redisPresent()) {
            assertFalse("sentinel".equalsIgnoreCase(inventory.redisMode()) && inventory.skipReason().isBlank());
            if (!"cluster".equalsIgnoreCase(inventory.redisMode())) {
                assertFalse(inventory.clusterEnabled());
            }
        } else {
            assertFalse(inventory.sentinelPresent());
            assertFalse(inventory.clusterEnabled());
            assertFalse(inventory.replicaPresent());
        }

        Map<String, String> report = G9SentinelClusterHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G9SentinelClusterHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g9_close"));
        assertEquals("false", report.get("policy_snapshot_redesigned"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        Path fixture = Path.of("scripts/g9-sentinel-cluster-honesty/fixtures/honest-skip.env");
        if (!fixture.toFile().isFile()) {
            fixture = Path.of("../scripts/g9-sentinel-cluster-honesty/fixtures/honest-skip.env");
        }
        G9SentinelClusterHonesty.verify(G9SentinelClusterHonesty.parseReport(fixture));
    }

    @Test
    void honestyGateRejectsFakeSentinelPass() {
        Map<String, String> fake = new HashMap<>(G9SentinelClusterHonesty.parseReport(fixture("fake-sentinel-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("sentinel_failover"));
    }

    @Test
    void honestyGateRejectsG9CloseClaim() {
        Map<String, String> fake = new HashMap<>(G9SentinelClusterHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g9_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G9SentinelClusterHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g9_close"));
    }

    @Test
    void infoFieldParsesStandaloneInventoryWithoutLiveRedis() {
        String server = "# Server\r\nredis_version:3.2.13\r\nredis_mode:standalone\r\n";
        String replication = "# Replication\r\nrole:master\r\nconnected_slaves:0\r\n";
        assertEquals("3.2.13", Redis32Client.infoField(server, "redis_version"));
        assertEquals("standalone", Redis32Client.infoField(server, "redis_mode"));
        assertEquals("master", Redis32Client.infoField(replication, "role"));
        assertEquals("0", Redis32Client.infoField(replication, "connected_slaves"));
        assertEquals("", Redis32Client.infoField(server, "missing"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g9-sentinel-cluster-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g9-sentinel-cluster-honesty/fixtures/" + name);
    }
}
