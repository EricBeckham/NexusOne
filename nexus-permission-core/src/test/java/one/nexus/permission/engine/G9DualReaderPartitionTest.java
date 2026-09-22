package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.redis.Redis32Client;
import one.nexus.permission.redis.RedisPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #121: in-process dual-reader partition. Two reader
 * nodes share the durable store but have independent LRU caches and
 * invalidate buses. A lost invalidate must not silently allow after a
 * durable DENY; each node converges only via its own version poll or
 * request-path {@code get}. Not Sentinel / Cluster / replica promotion,
 * not a live multi-JVM network partition.
 */
class G9DualReaderPartitionTest {
    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void lostInvalidateAcrossTwoReadersConvergesViaIndependentVersionPoll() {
        Partition fx = Partition.separateBuses();
        fx.bind();
        fx.seedGrant();
        long cached = fx.warmBothReaders();

        fx.writerDeny();
        fx.assertPartitionWindow(cached);
        assertTrue(fx.writerBus.wasNotified(TENANT.toString()));
        assertFalse(fx.readerABus.wasNotified(TENANT.toString()));
        assertFalse(fx.readerBBus.wasNotified(TENANT.toString()));

        assertEquals(1, new PolicySnapshotPoller(fx.readerA.snapshots(), Duration.ZERO).tick());
        assertTrue(fx.readerA.snapshots().cachedVersion(TENANT.toString()) > cached);
        assertEquals(cached, fx.readerB.snapshots().cachedVersion(TENANT.toString()),
                "reader A's poll must not mutate reader B's LRU");
        assertFalse(fx.readerB.snapshots().isMarkedInvalidated(TENANT.toString()));

        assertEquals(1, new PolicySnapshotPoller(fx.readerB.snapshots(), Duration.ZERO).tick());
        assertTrue(fx.readerB.snapshots().cachedVersion(TENANT.toString()) > cached);

        fx.assertBothFailClosed();
    }

    @Test
    void requestPathGetConvergesOnOneNodeWhilePeerLruStaysStale() {
        Partition fx = Partition.separateBuses();
        fx.bind();
        fx.seedGrant();
        long cached = fx.warmBothReaders();

        fx.writerDeny();
        fx.assertPartitionWindow(cached);

        Decision deniedA = fx.readerA.service().decide("ledger", "read");
        assertFalse(deniedA.allowed(), "request-path get must not silently allow");
        assertEquals("explicit_deny", deniedA.reason());
        assertTrue(fx.readerA.snapshots().cachedVersion(TENANT.toString()) > cached);
        assertEquals(cached, fx.readerB.snapshots().cachedVersion(TENANT.toString()),
                "reader A's get must not mutate reader B's LRU");
        assertFalse(fx.readerB.snapshots().isMarkedInvalidated(TENANT.toString()));

        Decision deniedB = fx.readerB.service().decide("ledger", "read");
        assertFalse(deniedB.allowed());
        assertEquals("explicit_deny", deniedB.reason());
        assertEquals(deniedA.policyVersion(), deniedB.policyVersion());
        fx.assertMatchesDurable(deniedA);
        fx.assertMatchesDurable(deniedB);
    }

    @Test
    void redisDownWriterDoesNotNotifyPartitionedReaders() {
        Partition fx = Partition.redisDownWriter();
        fx.bind();
        fx.seedGrant();
        assertFalse(fx.redisWriter.lastRemoteNotifySucceeded(),
                "Redis-down grant notify is not a remote success");
        long cached = fx.warmBothReaders();
        int failuresAfterGrant = fx.redisWriter.remoteNotifyFailureCount();

        fx.writerDeny();
        assertFalse(fx.redisWriter.lastRemoteNotifySucceeded());
        assertTrue(fx.redisWriter.remoteNotifyFailureCount() > failuresAfterGrant);
        RedisPolicyInvalidation.RemoteNotifyOutcome last = fx.redisWriter.lastRemoteNotify();
        assertFalse(last.published());
        assertFalse(last.versionHintWritten());
        fx.assertPartitionWindow(cached);
        assertFalse(fx.readerABus.wasNotified(TENANT.toString()));
        assertFalse(fx.readerBBus.wasNotified(TENANT.toString()));

        assertEquals(1, new PolicySnapshotPoller(fx.readerA.snapshots(), Duration.ZERO).tick());
        assertEquals(1, new PolicySnapshotPoller(fx.readerB.snapshots(), Duration.ZERO).tick());
        fx.assertBothFailClosed();
    }

    @Test
    void bothNodesMatchDurableEngineAfterIndependentPolls() {
        Partition fx = Partition.separateBuses();
        fx.bind();
        fx.seedGrant();
        long cached = fx.warmBothReaders();
        fx.writerDeny();
        fx.assertPartitionWindow(cached);

        assertEquals(1, new PolicySnapshotPoller(fx.readerA.snapshots(), Duration.ZERO).tick());
        assertEquals(1, new PolicySnapshotPoller(fx.readerB.snapshots(), Duration.ZERO).tick());

        Decision a = fx.readerA.service().decide("ledger", "read");
        Decision b = fx.readerB.service().decide("ledger", "read");
        PolicySnapshot durable = fx.policies.loadSnapshot(TENANT);
        Decision engine = PermissionEngine.evaluate(
                fx.readerA.subjects().resolve(TenantContextHolder.require()),
                TENANT.toString(),
                "ledger",
                "read",
                durable);
        assertFalse(a.allowed());
        assertEquals("explicit_deny", a.reason());
        assertEquals(engine.allowed(), a.allowed());
        assertEquals(engine.reason(), a.reason());
        assertEquals(engine.policyVersion(), a.policyVersion());
        assertEquals(engine.allowed(), b.allowed());
        assertEquals(engine.reason(), b.reason());
        assertEquals(engine.policyVersion(), b.policyVersion());
        assertEquals(1, durable.denies().size());
        assertEquals(1, durable.grants().size());
        assertEquals(durable.version(), fx.readerA.snapshots().cachedVersion(TENANT.toString()));
        assertEquals(durable.version(), fx.readerB.snapshots().cachedVersion(TENANT.toString()));
    }

    private static final class Partition {
        final InMemoryPolicyRepository policies;
        final PermissionModule writer;
        final PermissionModule readerA;
        final PermissionModule readerB;
        final InMemoryPolicyInvalidation writerBus;
        final InMemoryPolicyInvalidation readerABus;
        final InMemoryPolicyInvalidation readerBBus;
        final RedisPolicyInvalidation redisWriter;
        final UUID personId;
        final UUID accountId;

        private Partition(
                InMemoryPolicyRepository policies,
                PermissionModule writer,
                PermissionModule readerA,
                PermissionModule readerB,
                InMemoryPolicyInvalidation writerBus,
                InMemoryPolicyInvalidation readerABus,
                InMemoryPolicyInvalidation readerBBus,
                RedisPolicyInvalidation redisWriter,
                UUID personId,
                UUID accountId
        ) {
            this.policies = policies;
            this.writer = writer;
            this.readerA = readerA;
            this.readerB = readerB;
            this.writerBus = writerBus;
            this.readerABus = readerABus;
            this.readerBBus = readerBBus;
            this.redisWriter = redisWriter;
            this.personId = personId;
            this.accountId = accountId;
        }

        static Partition separateBuses() {
            InMemoryPolicyInvalidation writerBus = new InMemoryPolicyInvalidation();
            InMemoryPolicyInvalidation readerABus = new InMemoryPolicyInvalidation();
            InMemoryPolicyInvalidation readerBBus = new InMemoryPolicyInvalidation();
            return start(writerBus, readerABus, readerBBus, null);
        }

        static Partition redisDownWriter() {
            Redis32Client dead = new Redis32Client("127.0.0.1", 1, Duration.ofMillis(50));
            RedisPolicyInvalidation redisWriter = new RedisPolicyInvalidation(
                    dead, ex -> new AtomicInteger().incrementAndGet());
            InMemoryPolicyInvalidation readerABus = new InMemoryPolicyInvalidation();
            InMemoryPolicyInvalidation readerBBus = new InMemoryPolicyInvalidation();
            return start(null, readerABus, readerBBus, redisWriter);
        }

        private static Partition start(
                InMemoryPolicyInvalidation writerBus,
                InMemoryPolicyInvalidation readerABus,
                InMemoryPolicyInvalidation readerBBus,
                RedisPolicyInvalidation redisWriter
        ) {
            InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
            InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
            PermissionModule writer = PermissionModule.create(
                    policies, identity, redisWriter != null ? redisWriter : writerBus);
            PermissionModule readerA = PermissionModule.create(policies, identity, readerABus);
            PermissionModule readerB = PermissionModule.create(policies, identity, readerBBus);
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    identity, idp, new KeycloakSmtpHook(idp, false),
                    java.time.Clock.systemUTC(), Duration.ofHours(1));
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    TENANT, "G9 Partition Admin", "g9-partition@example.com", "g9-partition"));
            var person = identity.findPersonByAccount(TENANT,
                    identity.findAccountByEmail("g9-partition@example.com").orElseThrow().id()).orElseThrow();
            return new Partition(
                    policies, writer, readerA, readerB, writerBus, readerABus, readerBBus,
                    redisWriter, person.id(), person.accountId());
        }

        void bind() {
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), TENANT, accountId, personId, "g9-partition",
                    now, now.plus(Duration.ofHours(1))));
        }

        void seedGrant() {
            PolicyRepository.RoleRecord role = writer.admin().createRole(
                    "g9_self_reader", "G9 self reader", false);
            writer.admin().assign("user", personId.toString(), role.id());
            writer.admin().grant(
                    "GRANT", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
        }

        void writerDeny() {
            writer.admin().grant(
                    "DENY", "role", "g9_self_reader", "ledger", "read",
                    DataScopeKind.SELF, List.of(), Map.of());
        }

        long warmBothReaders() {
            Decision allowedA = readerA.service().decide("ledger", "read");
            Decision allowedB = readerB.service().decide("ledger", "read");
            assertTrue(allowedA.allowed());
            assertTrue(allowedB.allowed());
            long cachedA = readerA.snapshots().cachedVersion(TENANT.toString());
            long cachedB = readerB.snapshots().cachedVersion(TENANT.toString());
            assertEquals(cachedA, cachedB);
            return cachedA;
        }

        void assertPartitionWindow(long cached) {
            assertEquals(cached, readerA.snapshots().cachedVersion(TENANT.toString()),
                    "lost invalidate must leave reader A LRU on the GRANT snapshot");
            assertEquals(cached, readerB.snapshots().cachedVersion(TENANT.toString()),
                    "lost invalidate must leave reader B LRU on the GRANT snapshot");
            assertFalse(readerA.snapshots().isMarkedInvalidated(TENANT.toString()));
            assertFalse(readerB.snapshots().isMarkedInvalidated(TENANT.toString()));
        }

        void assertBothFailClosed() {
            Decision a = readerA.service().decide("ledger", "read");
            Decision b = readerB.service().decide("ledger", "read");
            assertFalse(a.allowed(), "partitioned reader A must not silently allow");
            assertFalse(b.allowed(), "partitioned reader B must not silently allow");
            assertEquals("explicit_deny", a.reason());
            assertEquals("explicit_deny", b.reason());
            assertMatchesDurable(a);
            assertMatchesDurable(b);
        }

        void assertMatchesDurable(Decision decided) {
            PolicySnapshot durable = policies.loadSnapshot(TENANT);
            Decision engine = PermissionEngine.evaluate(
                    readerA.subjects().resolve(TenantContextHolder.require()),
                    TENANT.toString(),
                    "ledger",
                    "read",
                    durable);
            assertEquals(engine.allowed(), decided.allowed());
            assertEquals(engine.reason(), decided.reason());
            assertEquals(engine.policyVersion(), decided.policyVersion());
        }
    }
}
