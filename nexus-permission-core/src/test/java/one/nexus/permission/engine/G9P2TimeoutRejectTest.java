package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 leftover: bounded P2-down then reject after timeout. Redis-down ordinary
 * fail-closed stays on {@link G9PolicySnapshotFailoverTest}. This suite proves
 * {@link PermissionService#decide} rejects when version/load hangs past the
 * configured clock, and that the healthy path is unchanged. Not Sentinel /
 * Cluster.
 */
class G9P2TimeoutRejectTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void decideRejectsAfterConfiguredTimeoutWhenVersionLoadHangs() {
        Harness harness = Harness.healthy();
        harness.bind();
        harness.grantLedgerRead();

        CountDownLatch hang = new CountDownLatch(1);
        Duration timeout = Duration.ofMillis(150);
        SnapshotManager hanging = new SnapshotManager(
                tenant -> {
                    try {
                        hang.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    return harness.policies.currentVersion(UUID.fromString(tenant));
                },
                tenant -> harness.policies.loadSnapshot(UUID.fromString(tenant)),
                timeout);
        PermissionService service = new PermissionService(
                harness.policies, new IamSubjectDirectory(harness.identity, harness.policies), hanging);

        long started = System.nanoTime();
        PermissionException ex = assertThrows(PermissionException.class, () -> service.decide("ledger", "read"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        hang.countDown();

        assertEquals("P2.PERM.0005", ex.errorCode().code());
        assertEquals(503, ex.httpStatus());
        assertTrue(ex.getMessage().contains("timed out after 150ms"), ex.getMessage());
        assertTrue(elapsedMs >= 120, "elapsed=" + elapsedMs);
        assertTrue(elapsedMs < 2_000, "must reject near the timeout, not hang; elapsed=" + elapsedMs);
    }

    @Test
    void healthyDecideStillMatchesDurablePermissionEngine() {
        Harness harness = Harness.healthy();
        harness.bind();

        Decision denied = harness.module.service().decide("ledger", "read");
        assertFalse(denied.allowed());
        assertEquals("no_grant", denied.reason());

        harness.grantLedgerRead();
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
        assertEquals(SnapshotManager.DEFAULT_LOAD_TIMEOUT, harness.module.snapshots().loadTimeout());
        assertEquals(0, harness.redisFailures.get());
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

        static Harness healthy() {
            InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
            InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
            AtomicInteger failures = new AtomicInteger();
            PermissionModule module = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                    java.time.Clock.systemUTC(), Duration.ofHours(1));
            UUID tenant = UUID.fromString("22222222-2222-4222-8222-222222222222");
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    tenant, "G9 Timeout Admin", "g9-timeout@example.com", "g9-timeout"));
            var person = identity.findPersonByAccount(tenant,
                    identity.findAccountByEmail("g9-timeout@example.com").orElseThrow().id()).orElseThrow();
            return new Harness(identity, policies, module, failures, tenant, person.id(), person.accountId());
        }

        void bind() {
            Instant now = Instant.now();
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(), tenant, accountId, personId, "g9-timeout", now, now.plus(Duration.ofHours(1))));
        }

        void grantLedgerRead() {
            PolicyRepository.RoleRecord role = module.admin().createRole("g9_timeout_reader", "G9 Timeout", false);
            module.admin().assign("user", personId.toString(), role.id());
            module.admin().grant(
                    "GRANT", "role", "g9_timeout_reader", "ledger", "read", DataScopeKind.SELF, List.of(), Map.of());
        }
    }
}
