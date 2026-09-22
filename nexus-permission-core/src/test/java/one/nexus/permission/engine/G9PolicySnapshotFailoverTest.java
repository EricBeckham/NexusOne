package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import one.nexus.permission.redis.PolicyCacheKeys;
import one.nexus.permission.redis.Redis32Client;
import one.nexus.permission.redis.RedisPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 evidence: Redis is invalidate-only. When Redis is down or the channel is
 * lost, {@link PermissionService} still decides from the durable policy store
 * through the existing {@link PermissionEngine}. Uses M0-04
 * {@link TenantContextHolder} only — no second tenant context.
 */
class G9PolicySnapshotFailoverTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void redisDownGrantStillDecidesFromDurableSnapshot() {
        Harness harness = Harness.redisDown();
        harness.bind();

        assertSame(one.nexus.p2.kernel.TenantContext.class, TenantContextHolder.require().getClass());
        Decision denied = harness.module.service().decide("ledger", "read");
        assertFalse(denied.allowed());
        assertEquals("no_grant", denied.reason());

        harness.grantLedgerRead();
        assertTrue(harness.redisFailures.get() >= 1, "Redis PUBLISH/SET must fail visibly, not become truth");

        Decision allowed = harness.module.service().decide("ledger", "read");
        PolicySnapshot durable = harness.policies.loadSnapshot(harness.tenant);
        Decision engine = PermissionEngine.evaluate(
                harness.module.subjects().resolve(TenantContextHolder.require()),
                harness.tenant.toString(),
                "ledger",
                "read",
                durable);

        assertTrue(allowed.allowed());
        assertEquals("allowed", allowed.reason());
        assertEquals(durable.version(), allowed.policyVersion());
        assertEquals(engine.allowed(), allowed.allowed());
        assertEquals(engine.reason(), allowed.reason());
        assertEquals(engine.policyVersion(), allowed.policyVersion());
        assertEquals(1, durable.grants().size());
        assertTrue(harness.policies.currentVersion(harness.tenant) > 1);
    }

    @Test
    void lostInvalidateChannelReloadsFromDurableVersionPoll() {
        Harness harness = Harness.redisDown();
        harness.bind();
        Decision before = harness.module.service().decide("ledger", "read");
        assertEquals("no_grant", before.reason());
        int failuresBeforeWrite = harness.redisFailures.get();

        // Other instance wrote the durable store; Redis PUBLISH never arrived.
        PolicyRepository.RoleRecord role = harness.policies.saveRole(
                InMemoryPolicyRepository.newRole(harness.tenant, "reader", "Reader", false));
        harness.policies.saveAssignment(new PolicyRepository.AssignmentRecord(
                UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
                harness.tenant,
                "user",
                harness.personId.toString(),
                role.id(),
                "reader"));
        harness.policies.saveGrant(new PolicyRepository.GrantRecord(
                UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"),
                harness.tenant,
                "GRANT",
                Rule.scoped("role", "reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of())));
        harness.policies.bumpVersion(harness.tenant);

        assertEquals(failuresBeforeWrite, harness.redisFailures.get(), "lost channel must not touch Redis");
        Decision after = harness.module.service().decide("ledger", "read");
        assertTrue(after.allowed());
        assertEquals("allowed", after.reason());
        assertEquals(harness.policies.currentVersion(harness.tenant), after.policyVersion());
        assertNotEquals(before.policyVersion(), after.policyVersion());
    }

    @Test
    void redisSnapshotKeyIsNeverPolicyTruth() {
        Harness harness = Harness.redisDown();
        harness.bind();
        harness.grantLedgerRead();

        String snapshotKey = PolicyCacheKeys.snapshot(harness.tenant.toString());
        assertTrue(snapshotKey.contains("cache:permission:snapshot"));
        assertTrue(PolicyCacheKeys.INVALIDATE_CHANNEL.startsWith("nexus:v1:"));

        // Poisoning Redis cannot change the decision: product never GET-loads policy from Redis.
        Redis32Client dead = new Redis32Client("127.0.0.1", 1, Duration.ofMillis(50));
        assertThrows(Redis32Client.RedisUnavailableException.class,
                () -> dead.setEx(snapshotKey, "{\"grants\":[]}", 30));

        Decision allowed = harness.module.service().decide("ledger", "read");
        assertTrue(allowed.allowed());
        assertEquals(harness.policies.loadSnapshot(harness.tenant).version(), allowed.policyVersion());
    }

    @Test
    void durableSourceFailureFailsClosedEvenWhenRedisNotifyIsAttempted() {
        Harness harness = Harness.redisDown();
        harness.bind();
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

        harness.module.invalidation().notifyTenant(harness.tenant.toString());
        assertTrue(harness.redisFailures.get() >= 1);
        PermissionException still = assertThrows(PermissionException.class, () -> service.decide("ledger", "read"));
        assertEquals("P2.PERM.0005", still.errorCode().code());
    }

    @Test
    void missingIssuedContextIsRejectedWithoutInventingTenantContext() {
        Harness harness = Harness.redisDown();
        PermissionException ex = assertThrows(
                PermissionException.class, () -> harness.module.service().decide("ledger", "read"));
        assertEquals("P2.PERM.0009", ex.errorCode().code());
        assertNull(TenantContextHolder.get());
    }

    private static final class Harness {
        private final InMemoryIdentityRepository identity;
        private final InMemoryPolicyRepository policies;
        private final PermissionModule module;
        private final AtomicInteger redisFailures;
        private final UUID tenant;
        private final UUID personId;
        private final UUID accountId;

        private Harness(
                InMemoryIdentityRepository identity,
                InMemoryPolicyRepository policies,
                PermissionModule module,
                AtomicInteger redisFailures,
                UUID tenant,
                UUID personId,
                UUID accountId
        ) {
            this.identity = identity;
            this.policies = policies;
            this.module = module;
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
            UUID tenant = UUID.fromString("99999999-9999-4999-8999-999999999999");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Admin", "g9-admin@example.com", "g9-admin"));
            var person = identity.findPersonByAccount(tenant,
                    identity.findAccountByEmail("g9-admin@example.com").orElseThrow().id()).orElseThrow();
            return new Harness(identity, policies, module, failures, tenant, person.id(), person.accountId());
        }

        void bind() {
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, accountId, personId, "g9-admin", now, now.plus(Duration.ofHours(1))));
        }

        void grantLedgerRead() {
            PolicyRepository.RoleRecord role = module.admin().createRole("g9_reader", "G9 Reader", false);
            module.admin().assign("user", personId.toString(), role.id());
            module.admin().grant(
                    "GRANT", "role", "g9_reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());
        }
    }
}
