package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.identity.IamSubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.redis.Redis32Client;
import one.nexus.permission.redis.RedisPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover beyond #83 / #89 / #100: Redis-down / lost-invalidate must
 * fail-closed after a durable DENY (no silent allow). Publish failure is
 * recorded, not treated as success. Readers still see the deny via version
 * poll. Uses M0-04 {@link TenantContextHolder} only — no second tenant
 * context. Not Sentinel / Cluster / replica promotion.
 */
class G9FailoverRemainderTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void redisDownDenyAfterGrantDoesNotSilentlyAllow() {
        Harness harness = Harness.redisDown();
        harness.bind();
        assertSame(TenantContext.class, TenantContextHolder.require().getClass());

        harness.grantLedgerRead();
        Decision allowed = harness.module.service().decide("ledger", "read");
        assertTrue(allowed.allowed());
        assertEquals("allowed", allowed.reason());
        assertFalse(harness.invalidation.lastRemoteNotifySucceeded(),
                "Redis-down grant notify is not a remote success");
        int failuresAfterGrant = harness.invalidation.remoteNotifyFailureCount();
        assertTrue(failuresAfterGrant >= 1);

        harness.denyLedgerRead();
        assertFalse(harness.invalidation.lastRemoteNotifySucceeded());
        assertTrue(harness.invalidation.remoteNotifyFailureCount() > failuresAfterGrant);
        RedisPolicyInvalidation.RemoteNotifyOutcome last = harness.invalidation.lastRemoteNotify();
        assertFalse(last.published());
        assertFalse(last.versionHintWritten());

        Decision denied = harness.module.service().decide("ledger", "read");
        PolicySnapshot durable = harness.policies.loadSnapshot(harness.tenant);
        Decision engine = PermissionEngine.evaluate(
                harness.module.subjects().resolve(TenantContextHolder.require()),
                harness.tenant.toString(),
                "ledger",
                "read",
                durable);

        assertFalse(denied.allowed(), "Redis-down DENY must not silently allow");
        assertEquals("explicit_deny", denied.reason());
        assertEquals(durable.version(), denied.policyVersion());
        assertEquals(engine.allowed(), denied.allowed());
        assertEquals(engine.reason(), denied.reason());
        assertEquals(engine.policyVersion(), denied.policyVersion());
        assertEquals(1, durable.denies().size());
        assertEquals(1, durable.grants().size());
    }

    @Test
    void lostInvalidateAfterDenyReloadsFailClosed() {
        Harness harness = Harness.redisDown();
        harness.bind();
        harness.grantLedgerRead();
        Decision before = harness.module.service().decide("ledger", "read");
        assertTrue(before.allowed());
        int failuresBeforeLostWrite = harness.redisFailures.get();

        // Other instance wrote DENY; Redis PUBLISH never arrived.
        harness.policies.saveGrant(new PolicyRepository.GrantRecord(
                UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
                harness.tenant,
                "DENY",
                Rule.scoped("role", "g9_reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of())));
        harness.policies.bumpVersion(harness.tenant);

        assertEquals(failuresBeforeLostWrite, harness.redisFailures.get(), "lost channel must not touch Redis");
        Decision after = harness.module.service().decide("ledger", "read");
        assertFalse(after.allowed(), "lost-channel DENY must not silently allow");
        assertEquals("explicit_deny", after.reason());
        assertEquals(harness.policies.currentVersion(harness.tenant), after.policyVersion());
        assertNotEquals(before.policyVersion(), after.policyVersion());
    }

    @Test
    void pollerTickSeesDenyWithoutInvalidateChannel() {
        Fixture fx = Fixture.startSeparateBuses();
        fx.bind();
        PolicyRepository.RoleRecord role = fx.writer.admin().createRole("g9_self_reader", "G9 self reader", false);
        fx.writer.admin().assign("user", fx.personId.toString(), role.id());
        fx.writer.admin().grant(
                "GRANT", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        Decision allowed = fx.reader.service().decide("ledger", "read");
        assertTrue(allowed.allowed());
        long cached = fx.reader.snapshots().cachedVersion(fx.tenant.toString());

        fx.writer.admin().grant(
                "DENY", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        assertEquals(cached, fx.reader.snapshots().cachedVersion(fx.tenant.toString()),
                "lost channel must not itself mutate the reader LRU");
        assertFalse(fx.reader.snapshots().isMarkedInvalidated(fx.tenant.toString()));

        PolicySnapshotPoller poller = new PolicySnapshotPoller(fx.reader.snapshots(), Duration.ZERO);
        assertEquals(1, poller.tick());
        assertTrue(fx.reader.snapshots().cachedVersion(fx.tenant.toString()) > cached);

        Decision denied = fx.reader.service().decide("ledger", "read");
        PolicySnapshot durable = fx.policies.loadSnapshot(fx.tenant);
        Decision engine = PermissionEngine.evaluate(
                fx.reader.subjects().resolve(TenantContextHolder.require()),
                fx.tenant.toString(),
                "ledger",
                "read",
                durable);
        assertFalse(denied.allowed(), "poller-reloaded DENY must not silently allow");
        assertEquals("explicit_deny", denied.reason());
        assertEquals(engine.allowed(), denied.allowed());
        assertEquals(engine.reason(), denied.reason());
        assertEquals(1, durable.denies().size());
    }

    @Test
    void durableMissNeverAllowsAfterDenyWrite() {
        Harness harness = Harness.redisDown();
        harness.bind();
        harness.grantLedgerRead();
        harness.denyLedgerRead();
        assertTrue(harness.module.service().decide("ledger", "read").reason().equals("explicit_deny"));

        SnapshotManager down = new SnapshotManager(
                tenant -> {
                    throw new IllegalStateException("iam.perm_policy_version unavailable");
                },
                tenant -> harness.policies.loadSnapshot(UUID.fromString(tenant)));
        PermissionService service = new PermissionService(
                harness.policies, new IamSubjectDirectory(harness.identity, harness.policies), down);

        PermissionException ex = assertThrows(PermissionException.class, () -> service.decide("ledger", "read"));
        assertEquals("P2.PERM.0005", ex.errorCode().code());
        assertEquals(503, ex.httpStatus());
        assertFalse(ex.errorCode().code().equals("P2.PERM.0003"), "must not fall through to silent no_grant/allow");
    }

    @Test
    void publishFailureIsNotTreatedAsSuccess() {
        Redis32Client dead = new Redis32Client("127.0.0.1", 1, Duration.ofMillis(50));
        AtomicInteger observed = new AtomicInteger();
        RedisPolicyInvalidation invalidation = new RedisPolicyInvalidation(dead, ex -> observed.incrementAndGet());

        assertFalse(invalidation.lastRemoteNotifySucceeded());
        invalidation.notifyTenant("99999999-9999-4999-8999-999999999999");
        assertFalse(invalidation.lastRemoteNotifySucceeded());
        RedisPolicyInvalidation.RemoteNotifyOutcome last = invalidation.lastRemoteNotify();
        assertFalse(last.published());
        assertFalse(last.versionHintWritten());
        assertTrue(last.failureCount() >= 1);
        assertTrue(observed.get() >= 1);
        assertTrue(invalidation.remoteNotifyFailureCount() >= 1);
    }

    private static final class Harness {
        private final InMemoryIdentityRepository identity;
        private final InMemoryPolicyRepository policies;
        private final PermissionModule module;
        private final RedisPolicyInvalidation invalidation;
        private final AtomicInteger redisFailures;
        private final UUID tenant;
        private final UUID personId;
        private final UUID accountId;

        private Harness(
                InMemoryIdentityRepository identity,
                InMemoryPolicyRepository policies,
                PermissionModule module,
                RedisPolicyInvalidation invalidation,
                AtomicInteger redisFailures,
                UUID tenant,
                UUID personId,
                UUID accountId
        ) {
            this.identity = identity;
            this.policies = policies;
            this.module = module;
            this.invalidation = invalidation;
            this.redisFailures = redisFailures;
            this.tenant = tenant;
            this.personId = personId;
            this.accountId = accountId;
        }

        static Harness redisDown() {
            InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
            InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
            AtomicInteger failures = new AtomicInteger();
            Redis32Client dead = new Redis32Client("127.0.0.1", 1, Duration.ofMillis(50));
            RedisPolicyInvalidation invalidation = new RedisPolicyInvalidation(dead, ex -> failures.incrementAndGet());
            PermissionModule module = PermissionModule.create(policies, identity, invalidation);
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                    java.time.Clock.systemUTC(), Duration.ofHours(1));
            UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Failover Admin", "g9-failover@example.com", "g9-failover"));
            var person = identity.findPersonByAccount(tenant,
                    identity.findAccountByEmail("g9-failover@example.com").orElseThrow().id()).orElseThrow();
            return new Harness(identity, policies, module, invalidation, failures, tenant, person.id(), person.accountId());
        }

        void bind() {
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, accountId, personId, "g9-failover", now, now.plus(Duration.ofHours(1))));
        }

        void grantLedgerRead() {
            PolicyRepository.RoleRecord role = module.admin().createRole("g9_reader", "G9 Reader", false);
            module.admin().assign("user", personId.toString(), role.id());
            module.admin().grant(
                    "GRANT", "role", "g9_reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());
        }

        void denyLedgerRead() {
            module.admin().grant(
                    "DENY", "role", "g9_reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());
        }
    }

    private static final class Fixture {
        final InMemoryPolicyRepository policies;
        final PermissionModule writer;
        final PermissionModule reader;
        final UUID tenant;
        final UUID personId;
        final UUID accountId;

        private Fixture(
                InMemoryPolicyRepository policies,
                PermissionModule writer,
                PermissionModule reader,
                UUID tenant,
                UUID personId,
                UUID accountId
        ) {
            this.policies = policies;
            this.writer = writer;
            this.reader = reader;
            this.tenant = tenant;
            this.personId = personId;
            this.accountId = accountId;
        }

        static Fixture startSeparateBuses() {
            InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
            InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
            PermissionModule writer = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
            PermissionModule reader = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                    java.time.Clock.systemUTC(), Duration.ofHours(1));
            UUID tenant = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Failover Poll", "g9-failover-poll@example.com", "g9-failover-poll"));
            var person = identity.findPersonByAccount(tenant,
                    identity.findAccountByEmail("g9-failover-poll@example.com").orElseThrow().id()).orElseThrow();
            return new Fixture(policies, writer, reader, tenant, person.id(), person.accountId());
        }

        void bind() {
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, accountId, personId, "g9-failover-poll",
                    now, now.plus(Duration.ofHours(1))));
        }
    }
}
