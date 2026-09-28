package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.effective.EffectivePermissionService;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Pla8OrgDemoSeedTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void writesOrgAndPositionRolesThroughTheApisAndRepeatsCleanly() {
        Harness harness = harness();
        String local = "jdbc:postgresql://127.0.0.1:5432/nexusone";
        Pla8OrgDemoSeedConfiguration.run("true", "local", local, harness.identity, harness.permissions, harness.seeder);
        Pla8OrgDemoSeedConfiguration.run("true", "local", local, harness.identity, harness.permissions, harness.seeder);

        UUID tenant = P1DemoSeedConfiguration.TENANT_ID;
        assertTrue(harness.identity.repository().findDepartmentByCode(tenant, Pla8OrgDemoContent.DEPARTMENT).isPresent());
        Model.Person member = harness.identity.repository()
                .findPersonByEmployeeNo(tenant, Pla8OrgDemoContent.EMPLOYEE_MEMBER).orElseThrow();
        Model.Person admin = harness.identity.repository()
                .findPersonByEmployeeNo(tenant, Pla8OrgDemoContent.EMPLOYEE_ADMIN).orElseThrow();
        assertEquals(1, harness.identity.repository().listEmploymentsByPerson(tenant, member.id()).size());

        EffectivePermissionService preview = new EffectivePermissionService(harness.permissions, harness.identity.repository());
        TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(
                UUID.randomUUID(), tenant, Pla8OrgDemoContent.ACCOUNT_ADMIN, admin.id(), "pla8",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(60)));
        Set<String> memberRoles = preview.forPerson(member.id()).roles().stream()
                .map(EffectivePermissionService.RoleSource::roleCode).collect(Collectors.toSet());
        Set<String> adminRoles = preview.forPerson(admin.id()).roles().stream()
                .map(EffectivePermissionService.RoleSource::roleCode).collect(Collectors.toSet());
        assertEquals(Set.of(TenantMemberGrantSeeder.ROLE_CODE), memberRoles);
        assertTrue(adminRoles.contains(TenantMemberGrantSeeder.ROLE_CODE));
        assertTrue(adminRoles.contains(TenantMemberGrantSeeder.DESIGNER_ROLE));
    }

    @Test
    void staysQuietWhenTheDemoSwitchIsOff() {
        Harness harness = harness();
        Pla8OrgDemoSeedConfiguration.run("false", "local", "jdbc:postgresql://127.0.0.1:5432/nexusone",
                harness.identity, harness.permissions, harness.seeder);
        assertTrue(harness.identity.repository().listDepartments(P1DemoSeedConfiguration.TENANT_ID).isEmpty());
    }

    @Test
    void refusesARemoteDatabaseBeforeWriting() {
        Harness harness = harness();
        assertThrows(IllegalStateException.class, () -> Pla8OrgDemoSeedConfiguration.run(
                "true", "local", "jdbc:postgresql://db.internal:5432/nexusone",
                harness.identity, harness.permissions, harness.seeder));
        assertTrue(harness.identity.repository().listDepartments(P1DemoSeedConfiguration.TENANT_ID).isEmpty());
    }

    private static Harness harness() {
        IdentityModule identity = new IdentityModule(new InMemoryIdentityRepository(), new StubIdentityProvider(),
                new KeycloakSmtpHook(new StubIdentityProvider(), false), Clock.systemUTC(), Duration.ofHours(1));
        PermissionModule permissions = PermissionModule.create(new InMemoryPolicyRepository(), identity.repository(),
                new InMemoryPolicyInvalidation());
        TenantMemberGrantSeeder seeder = new TenantMemberGrantSeeder(permissions, identity.repository(), MemberAccessMode.LEGACY);
        return new Harness(identity, permissions, seeder);
    }

    private record Harness(IdentityModule identity, PermissionModule permissions, TenantMemberGrantSeeder seeder) {}
}
