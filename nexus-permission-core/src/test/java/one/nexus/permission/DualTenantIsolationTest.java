package one.nexus.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DualTenantIsolationTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void grantsAndSnapshotsDoNotCrossTenant() {
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
        PermissionModule module = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
        StubIdentityProvider idp = new StubIdentityProvider();
        one.nexus.p2.IdentityModule iam = new one.nexus.p2.IdentityModule(
                identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                java.time.Clock.systemUTC(), Duration.ofHours(1));

        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Admin A", "perm-a@example.com", "perm-a"));
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantB, "Admin B", "perm-b@example.com", "perm-b"));

        var personA = identity.findPersonByAccount(tenantA,
                identity.findAccountByEmail("perm-a@example.com").orElseThrow().id()).orElseThrow();
        var personB = identity.findPersonByAccount(tenantB,
                identity.findAccountByEmail("perm-b@example.com").orElseThrow().id()).orElseThrow();

        bind(tenantA, personA.accountId(), personA.id());
        PolicyRepository.RoleRecord roleA = module.admin().createRole("employee", "Employee", false);
        module.admin().assign("user", personA.id().toString(), roleA.id());
        module.admin().grant("GRANT", "role", "employee", "ledger", "read", DataScopeKind.SELF, List.of(), java.util.Map.of());

        bind(tenantB, personB.accountId(), personB.id());
        PolicyRepository.RoleRecord roleB = module.admin().createRole("employee", "Employee", false);
        module.admin().assign("user", personB.id().toString(), roleB.id());

        PolicySnapshot snapA = policies.loadSnapshot(tenantA);
        PolicySnapshot snapB = policies.loadSnapshot(tenantB);
        assertEquals(1, snapA.grants().size());
        assertTrue(snapB.grants().isEmpty());
        assertEquals(tenantA.toString(), snapA.tenantId());
        assertEquals(tenantB.toString(), snapB.tenantId());

        var subjectA = module.subjects().resolve(new TenantContext(
                UUID.randomUUID(), tenantA, personA.accountId(), personA.id(), "a", Instant.now(), Instant.now().plusSeconds(60)));
        Decision cross = PermissionEngine.evaluate(subjectA, tenantB.toString(), "ledger", "read", snapA);
        assertEquals("tenant_boundary", cross.reason());
        assertFalse(cross.allowed());

        Decision allowed = PermissionEngine.evaluate(subjectA, tenantA.toString(), "ledger", "read", snapA);
        assertTrue(allowed.allowed());
        assertEquals(Set.of(), policies.roleCodesFor(tenantB, personA.id().toString(), Set.of(), Set.of()));
    }

    private static void bind(UUID tenantId, UUID accountId, UUID personId) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, accountId, personId, "test", now, now.plus(Duration.ofHours(1))));
    }
}
