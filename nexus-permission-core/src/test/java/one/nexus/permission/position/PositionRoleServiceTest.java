package one.nexus.permission.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionRoleServiceTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a01");
    IdentityModule iam;
    PermissionModule permissions;
    PositionRoleService bindings;
    TenantContext adminContext;
    UUID clerkPosition;
    UUID clerk;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var admin = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla1-admin@example.com", "pla1-admin"));
        adminContext = context(admin.personId());
        TenantContextHolder.set(adminContext);
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(),
                new InMemoryPolicyInvalidation());
        bindings = new PositionRoleService(permissions, iam.repository());

        // A clerk: own position, ACTIVE employment, no personal roles.
        Model.Position position = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null));
        clerkPosition = position.id();
        Model.Department department = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        Model.Person person = iam.organization().createPerson(
                new OrganizationService.PersonCommand("Clerk", "C-1", "clerk@example.com", null, null, null));
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                person.id(), department.id(), position.id(), Model.EmploymentKind.PRIMARY, LocalDate.now(), null));
        clerk = person.id();
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    TenantContext context(UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    UUID role(String code, String resource, String action) {
        var role = permissions.admin().createRole(code, code, false);
        permissions.admin().grant("GRANT", "role", code, resource, action, DataScopeKind.ALL, List.of(), java.util.Map.of());
        return role.id();
    }

    boolean clerkMay(String resource, String action) {
        TenantContextHolder.set(context(clerk));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    @Test
    void aPersonGetsTheRolesOfTheirPosition() {
        UUID reader = role("ledger_reader", "ledger", "read");
        assertFalse(clerkMay("ledger", "read"));

        List<PositionRoleService.BoundRole> bound = bindings.set(clerkPosition, List.of(reader));

        assertEquals(1, bound.size());
        assertEquals("ledger_reader", bound.getFirst().roleCode());
        assertTrue(clerkMay("ledger", "read"), "role reaches the person through the ACTIVE employment");
        assertEquals(List.of(bound.getFirst()), bindings.all().get(clerkPosition));
    }

    @Test
    void setReplacesTheBindingAndIsIdempotent() {
        UUID reader = role("ledger_reader", "ledger", "read");
        UUID writer = role("ledger_writer", "ledger", "write");
        bindings.set(clerkPosition, List.of(reader, writer));
        bindings.set(clerkPosition, List.of(reader, writer));
        assertEquals(2, bindings.rolesOf(clerkPosition).size(), "no duplicate bindings");

        bindings.set(clerkPosition, List.of(writer));
        assertEquals(List.of("ledger_writer"), bindings.rolesOf(clerkPosition).stream().map(PositionRoleService.BoundRole::roleCode).toList());
        assertFalse(clerkMay("ledger", "read"), "removed role no longer applies");
        assertTrue(clerkMay("ledger", "write"));

        bindings.set(clerkPosition, List.of());
        assertTrue(bindings.rolesOf(clerkPosition).isEmpty());
        assertFalse(clerkMay("ledger", "write"));
    }

    @Test
    void unknownPositionsOrRolesAndOtherTenantsAreRefusedWithoutChanges() {
        UUID reader = role("ledger_reader", "ledger", "read");
        assertThrows(PermissionException.class, () -> bindings.set(UUID.randomUUID(), List.of(reader)));
        assertThrows(PermissionException.class, () -> bindings.set(clerkPosition, List.of(reader, UUID.randomUUID())));
        assertTrue(bindings.rolesOf(clerkPosition).isEmpty(), "an invalid request changes nothing");

        // The same position id seen from another tenant does not exist there.
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "x",
                Instant.now(), Instant.now().plusSeconds(60)));
        assertThrows(PermissionException.class, () -> bindings.rolesOf(clerkPosition));
    }

    @Test
    void conflictingPlatformDutiesCannotShareAPosition() {
        permissions.duties().install();
        UUID system = permissions.repository().findRoleByCode(tenant, PlatformDutyCatalog.SYSTEM_ADMIN).orElseThrow().id();
        UUID audit = permissions.repository().findRoleByCode(tenant, PlatformDutyCatalog.AUDIT_ADMIN).orElseThrow().id();
        assertThrows(PermissionException.class, () -> bindings.set(clerkPosition, List.of(system, audit)));
        assertTrue(bindings.rolesOf(clerkPosition).isEmpty());
        assertEquals(1, bindings.set(clerkPosition, List.of(system)).size());
    }

    @Test
    void anEndedEmploymentDropsThePositionRoles() {
        UUID reader = role("ledger_reader", "ledger", "read");
        bindings.set(clerkPosition, List.of(reader));
        assertTrue(clerkMay("ledger", "read"));
        var employment = iam.organization().listEmployments(clerk).getFirst();
        iam.organization().endEmployment(employment.id());
        assertFalse(clerkMay("ledger", "read"));
    }
}
