package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.SurfaceCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class DemoAcmeRoleSplitTest {
    private static final UUID TENANT = P1DemoSeedConfiguration.TENANT_ID;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void closedSwitchesInstallNothing() throws Exception {
        PermissionModule permissions = module(new InMemoryIdentityRepository());
        new DemoAcmeRoleSplitRunner(new MockEnvironment(), permissions).run(new DefaultApplicationArguments());
        assertTrue(permissions.repository().listRoles(TENANT).isEmpty());
    }

    @Test
    void dutyWindowRefusesDemoContext() {
        var env = new MockEnvironment()
                .withProperty(PlatformDutyBootstrap.ENABLED, "true")
                .withProperty(PlatformDutyBootstrap.DEMO_CONTEXT, "true");
        PermissionModule permissions = module(new InMemoryIdentityRepository());
        assertThrows(IllegalStateException.class,
                () -> new DemoAcmeRoleSplitRunner(env, permissions).run(new DefaultApplicationArguments()));
        assertTrue(permissions.repository().listRoles(TENANT).isEmpty());
    }

    @Test
    void tenantAdminCanExportAndMemberCannot() {
        InMemoryIdentityRepository identity = new InMemoryIdentityRepository();
        save(identity, P2DemoSeedConfiguration.PERSON_LIN, "林晓");
        save(identity, P2DemoSeedConfiguration.PERSON_ZHOU, "周然");
        PermissionModule permissions = module(identity);

        DemoAcmeRoleSplit.install(permissions);
        DemoAcmeRoleSplit.install(permissions);

        long adminRoles = permissions.repository().listAssignments(TENANT).stream()
                .filter(item -> P2DemoSeedConfiguration.PERSON_LIN.toString().equals(item.principalId())
                        && DemoAcmeRoleSplit.TENANT_ADMIN.equals(item.roleCode()))
                .count();
        assertEquals(1, adminRoles);
        assertTrue(permissions.repository().listAssignments(TENANT).stream()
                .noneMatch(item -> P2DemoSeedConfiguration.PERSON_ZHOU.toString().equals(item.principalId())
                        && DemoAcmeRoleSplit.TENANT_ADMIN.equals(item.roleCode())));
        assertTrue(permissions.repository().listAssignments(TENANT).stream()
                .anyMatch(item -> P2DemoSeedConfiguration.PERSON_ZHOU.toString().equals(item.principalId())
                        && TenantMemberGrantSeeder.ROLE_CODE.equals(item.roleCode())));

        bind(P2DemoSeedConfiguration.PERSON_LIN);
        permissions.service().check("ledger", "read");
        permissions.service().check(SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);
        permissions.service().check(DemoAcmeRoleSplit.MANAGE_RESOURCE, DemoAcmeRoleSplit.MANAGE_ACTION);

        bind(P2DemoSeedConfiguration.PERSON_ZHOU);
        permissions.service().check("ledger", "read");
        var export = permissions.service().decide(SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);
        var manage = permissions.service().decide(DemoAcmeRoleSplit.MANAGE_RESOURCE, DemoAcmeRoleSplit.MANAGE_ACTION);
        assertFalse(export.allowed());
        assertEquals("no_grant", export.reason());
        assertFalse(manage.allowed());
        assertEquals("no_grant", manage.reason());
    }

    private static void bind(UUID personId) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, null, personId, "split-test",
                Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static PermissionModule module(InMemoryIdentityRepository identity) {
        return PermissionModule.create(new InMemoryPolicyRepository(), identity, new InMemoryPolicyInvalidation());
    }

    private static void save(InMemoryIdentityRepository identity, UUID personId, String name) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        identity.savePerson(new Model.Person(
                personId, TENANT, UUID.randomUUID(), name, name, name + "@demo.invalid", null,
                LocalDate.parse("2024-01-01"), Model.PersonStatus.ACTIVE, now, now, null, 0));
    }
}
