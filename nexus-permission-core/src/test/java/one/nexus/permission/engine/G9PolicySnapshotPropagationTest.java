package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G9 remainder: permission writes propagate through DB version polling, and
 * Redis/in-process invalidate only accelerates. Does not use {@code tenant_member}
 * and does not claim Sentinel / P2-down failover.
 */
class G9PolicySnapshotPropagationTest {
    private static final UUID TENANT = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void channelLossStillPropagatesViaVersionPoll() {
        Fixture fx = Fixture.startSeparateBuses();
        bind(fx.person.accountId(), fx.person.id());
        PolicyRepository.RoleRecord role = fx.writer.admin().createRole("g9_self_reader", "G9 self reader", false);
        fx.writer.admin().assign("user", fx.person.id().toString(), role.id());

        Decision before = fx.reader.service().decide("ledger", "read");
        assertFalse(before.allowed());
        assertEquals("no_grant", before.reason());
        long cached = fx.reader.snapshots().cachedVersion(TENANT.toString());
        assertTrue(cached >= 1);

        fx.writer.admin().grant(
                "GRANT", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        assertEquals(cached, fx.reader.snapshots().cachedVersion(TENANT.toString()),
                "lost channel must not itself mutate the reader LRU");
        assertFalse(fx.reader.snapshots().isMarkedInvalidated(TENANT.toString()));

        Decision after = fx.reader.service().decide("ledger", "read");
        assertTrue(after.allowed(), after.reason());
        assertTrue(fx.reader.snapshots().cachedVersion(TENANT.toString()) > cached);
        assertEquals(1, fx.reader.service().snapshot().grants().size());
    }

    @Test
    void sharedInvalidateMarksReaderBeforeNextGet() {
        Fixture fx = Fixture.startSharedBus();
        bind(fx.person.accountId(), fx.person.id());
        PolicyRepository.RoleRecord role = fx.writer.admin().createRole("g9_self_reader", "G9 self reader", false);
        fx.writer.admin().assign("user", fx.person.id().toString(), role.id());
        assertFalse(fx.reader.service().decide("ledger", "read").allowed());

        fx.writer.admin().grant(
                "GRANT", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        assertTrue(fx.readerBus.wasNotified(TENANT.toString()));
        assertTrue(fx.reader.snapshots().isMarkedInvalidated(TENANT.toString()));

        Decision after = fx.reader.service().decide("ledger", "read");
        assertTrue(after.allowed(), after.reason());
        assertFalse(fx.reader.snapshots().isMarkedInvalidated(TENANT.toString()));
    }

    @Test
    void pollerTickReloadsCachedTenantWithoutCallerGet() {
        Fixture fx = Fixture.startSeparateBuses();
        bind(fx.person.accountId(), fx.person.id());
        PolicyRepository.RoleRecord role = fx.writer.admin().createRole("g9_self_reader", "G9 self reader", false);
        fx.writer.admin().assign("user", fx.person.id().toString(), role.id());
        fx.reader.service().decide("ledger", "read");
        long cached = fx.reader.snapshots().cachedVersion(TENANT.toString());

        fx.writer.admin().grant(
                "GRANT", "role", "g9_self_reader", "ledger", "read",
                DataScopeKind.SELF, List.of(), Map.of());
        assertEquals(cached, fx.reader.snapshots().cachedVersion(TENANT.toString()));

        PolicySnapshotPoller poller = new PolicySnapshotPoller(fx.reader.snapshots(), Duration.ZERO);
        assertEquals(1, poller.tick());
        assertTrue(fx.reader.snapshots().cachedVersion(TENANT.toString()) > cached);
        assertTrue(fx.reader.service().decide("ledger", "read").allowed());
    }

    @Test
    void seedRoleTenantMemberIsNotUsedAsTheGrant() {
        Fixture fx = Fixture.startSeparateBuses();
        bind(fx.person.accountId(), fx.person.id());
        fx.writer.admin().createRole("g9_self_reader", "G9 self reader", false);
        assertTrue(fx.policies.findRoleByCode(TENANT, "tenant_member").isEmpty());
        assertTrue(fx.writer.repository().listGrants(TENANT).isEmpty());
    }

    private static void bind(UUID accountId, UUID personId) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, accountId, personId, "g9", now, now.plus(Duration.ofHours(1))));
    }

    private static final class Fixture {
        final InMemoryPolicyRepository policies;
        final PermissionModule writer;
        final PermissionModule reader;
        final InMemoryPolicyInvalidation readerBus;
        final Model.Person person;

        private Fixture(
                InMemoryPolicyRepository policies,
                PermissionModule writer,
                PermissionModule reader,
                InMemoryPolicyInvalidation readerBus,
                Model.Person person
        ) {
            this.policies = policies;
            this.writer = writer;
            this.reader = reader;
            this.readerBus = readerBus;
            this.person = person;
        }

        static Fixture startSeparateBuses() {
            return start(new InMemoryPolicyInvalidation(), new InMemoryPolicyInvalidation());
        }

        static Fixture startSharedBus() {
            InMemoryPolicyInvalidation bus = new InMemoryPolicyInvalidation();
            return start(bus, bus);
        }

        private static Fixture start(InMemoryPolicyInvalidation writerBus, InMemoryPolicyInvalidation readerBus) {
            InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
            InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
            PermissionModule writer = PermissionModule.create(policies, identity, writerBus);
            PermissionModule reader = PermissionModule.create(policies, identity, readerBus);
            StubIdentityProvider idp = new StubIdentityProvider();
            IdentityModule iam = new IdentityModule(
                    identity, idp, new KeycloakSmtpHook(idp, false), java.time.Clock.systemUTC(), Duration.ofHours(1));
            iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                    TENANT, "G9 Admin", "g9-admin@example.com", "g9-admin"));
            var person = identity.findPersonByAccount(TENANT,
                    identity.findAccountByEmail("g9-admin@example.com").orElseThrow().id()).orElseThrow();
            return new Fixture(policies, writer, reader, readerBus, person);
        }
    }
}
