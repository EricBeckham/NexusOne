package one.nexus.permission.admin;

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
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.position.PositionRoleService;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PLA5: the seeder under each member-access mode. */
class MemberAccessModeSeederTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a05");
    IdentityModule iam;
    PermissionModule permissions;
    PolicyRepository repository;
    TenantContext adminContext;
    UUID clerkPosition;
    UUID clerk;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var admin = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla5-admin@example.com", "pla5-admin"));
        adminContext = context(admin.personId());
        TenantContextHolder.set(adminContext);
        repository = new InMemoryPolicyRepository();
        permissions = PermissionModule.create(repository, iam.repository(), new InMemoryPolicyInvalidation());

        Model.Department office = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        clerkPosition = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null)).id();
        clerk = iam.organization().createPerson(new OrganizationService.PersonCommand(
                "Clerk", "C-5", "clerk5@example.com", null, null, UUID.randomUUID())).id();
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                clerk, office.id(), clerkPosition, Model.EmploymentKind.PRIMARY, LocalDate.now(), null));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    TenantContext context(UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    void enter(MemberAccessMode mode, UUID person) {
        TenantContextHolder.set(context(person));
        new TenantMemberGrantSeeder(permissions, iam.repository(), mode).ensure();
        TenantContextHolder.set(adminContext);
    }

    boolean may(UUID person, String resource, String action) {
        TenantContextHolder.set(context(person));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    long personalSeeds() {
        return repository.listAssignments(tenant).stream().filter(item -> "user".equals(item.principalType())).count();
    }

    @Test
    void legacySeedsEveryPersonWhoEnters() {
        enter(MemberAccessMode.LEGACY, clerk);
        assertEquals(1, personalSeeds());
        assertTrue(may(clerk, "ledger", "read"));
    }

    @Test
    void positionModeDefinesTheRolesButOnlyPositionBindingsGrantThem() {
        enter(MemberAccessMode.POSITION, clerk);

        assertTrue(repository.findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).isPresent());
        assertTrue(repository.findRoleByCode(tenant, TenantMemberGrantSeeder.DESIGNER_ROLE).isPresent());
        assertEquals(0, personalSeeds());
        assertFalse(may(clerk, "ledger", "read"), "no binding yet");

        UUID member = repository.findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).orElseThrow().id();
        new PositionRoleService(permissions, iam.repository()).set(clerkPosition, List.of(member));
        assertTrue(may(clerk, "ledger", "read"), "the member role now reaches the clerk through the position");
        assertFalse(may(clerk, "process.definition", "write"));
    }

    @Test
    void unknownModesAreRejectedAndBlankMeansLegacy() {
        assertEquals(MemberAccessMode.LEGACY, MemberAccessMode.parse(null));
        assertEquals(MemberAccessMode.LEGACY, MemberAccessMode.parse(" "));
        assertEquals(MemberAccessMode.POSITION, MemberAccessMode.parse("position"));
        assertThrows(IllegalArgumentException.class, () -> MemberAccessMode.parse("everyone"));
    }
}
