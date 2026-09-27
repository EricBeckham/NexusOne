package one.nexus.permission.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantMemberGrantSeederTest {
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void seedsRoleGrantsAndAssignsCurrentPerson() {
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
        PermissionModule module = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
        StubIdentityProvider idp = new StubIdentityProvider();
        one.nexus.p2.IdentityModule iam = new one.nexus.p2.IdentityModule(
                identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                java.time.Clock.systemUTC(), Duration.ofHours(1));
        UUID tenant = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Seed Admin", "seed-admin@example.com", "seed-admin"));
        var person = identity.findPersonByAccount(tenant,
                identity.findAccountByEmail("seed-admin@example.com").orElseThrow().id()).orElseThrow();
        TenantContextHolder.set(new TenantContext(
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                tenant,
                person.accountId(),
                person.id(),
                "seed-admin",
                Instant.now(),
                Instant.now().plusSeconds(3600)));

        TenantMemberGrantSeeder seeder = new TenantMemberGrantSeeder(module);
        seeder.ensure();
        seeder.ensure();

        assertTrue(policies.findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).isPresent());
        assertEquals(1, policies.listAssignments(tenant).stream()
                .filter(item -> "user".equals(item.principalType())
                        && person.id().toString().equals(item.principalId()))
                .count());
        Decision decision = module.service().decide("ledger", "read");
        assertTrue(decision.allowed(), decision.reason());
        assertTrue(module.service().decide("ledger", "write").allowed());
        assertTrue(module.service().decide("form", "read").allowed());
        assertTrue(module.service().decide("form", "write").allowed());
        assertTrue(module.service().decide("process.task", "read").allowed());
        assertTrue(module.service().decide("process.task", "approve").allowed());
        assertTrue(module.service().decide("process.task", "reject").allowed());
        assertTrue(module.service().decide("process.instance", "start").allowed());
        assertTrue(module.service().decide("process.instance", "withdraw").allowed());
        assertTrue(module.service().decide("process.definition", "read").allowed());
        // PV4: tenant_member alone no longer designs processes; FA3: nor configures post-completion actions.
        assertFalse(module.service().decide("process.definition", "write").allowed());
        assertFalse(module.service().decide("process.action", "read").allowed());
        assertFalse(module.service().decide("process.action", "write").allowed());
        assertTrue(policies.listAssignments(tenant).stream()
                .noneMatch(item -> "TENANT_ADMIN".equals(item.principalId())));
    }

    record Fixture(InMemoryPolicyRepository policies, PermissionModule module, InMemoryIdentityRepository identity,
            UUID tenant, UUID personId) {}

    static Fixture signedInAdmin() {
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        InMemoryPolicyRepository policies = new InMemoryPolicyRepository();
        PermissionModule module = PermissionModule.create(policies, identity, new InMemoryPolicyInvalidation());
        StubIdentityProvider idp = new StubIdentityProvider();
        one.nexus.p2.IdentityModule iam = new one.nexus.p2.IdentityModule(
                identity, idp, new one.nexus.p2.smtp.KeycloakSmtpHook(idp, false),
                java.time.Clock.systemUTC(), Duration.ofHours(1));
        UUID tenant = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccc004");
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "PV4 Admin", "pv4-admin@example.com", "pv4-admin"));
        var person = identity.findPersonByAccount(tenant,
                identity.findAccountByEmail("pv4-admin@example.com").orElseThrow().id()).orElseThrow();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, person.accountId(), person.id(),
                "pv4-admin", Instant.now(), Instant.now().plusSeconds(3600)));
        return new Fixture(policies, module, identity, tenant, person.id());
    }

    @Test
    void aTenantAdminDesignsProcessesThroughTheDesignerRole() {
        Fixture f = signedInAdmin();
        TenantMemberGrantSeeder seeder = new TenantMemberGrantSeeder(f.module(), f.identity());
        seeder.ensure();
        seeder.ensure();

        assertTrue(f.module().service().decide("process.definition", "write").allowed());
        assertTrue(f.policies().findRoleByCode(f.tenant(), TenantMemberGrantSeeder.DESIGNER_ROLE).isPresent());
        // Designer grants (PV4 process design + FA3 process.action read/write), each exactly once after two seeds.
        assertEquals(3, f.policies().listGrants(f.tenant()).stream()
                .filter(g -> g.rule() != null && TenantMemberGrantSeeder.DESIGNER_ROLE.equals(g.rule().principalId()))
                .count(), "idempotent designer grants");
        assertTrue(f.module().service().decide("process.action", "read").allowed());
        assertTrue(f.module().service().decide("process.action", "write").allowed());
        assertTrue(f.policies().listGrants(f.tenant()).stream().noneMatch(g -> g.rule() != null
                && TenantMemberGrantSeeder.ROLE_CODE.equals(g.rule().principalId())
                && "process.definition".equals(g.rule().resource()) && "write".equals(g.rule().action())));
        // Filling in forms stays with every member.
        assertTrue(f.module().service().decide("form", "write").allowed());
    }

    @Test
    void theOldMemberWideDesignGrantIsRetired() {
        Fixture f = signedInAdmin();
        // What earlier builds left behind: tenant_member holding process.definition:write.
        new TenantMemberGrantSeeder(f.module()).ensure();
        f.module().admin().grant("GRANT", "role", TenantMemberGrantSeeder.ROLE_CODE, "process.definition", "write",
                one.nexus.permission.engine.DataScopeKind.ALL, java.util.List.of(), java.util.Map.of());
        assertTrue(f.module().service().decide("process.definition", "write").allowed());
        long before = f.policies().currentVersion(f.tenant());

        new TenantMemberGrantSeeder(f.module()).ensure();

        assertFalse(f.module().service().decide("process.definition", "write").allowed());
        assertTrue(f.policies().currentVersion(f.tenant()) > before, "policy version republished");
        assertTrue(f.module().service().decide("process.definition", "read").allowed());
    }
}
