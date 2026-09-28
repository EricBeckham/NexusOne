package one.nexus.permission.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberAccessConversionServiceTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a05");
    IdentityModule iam;
    PermissionModule permissions;
    PolicyRepository repository;
    TenantContext adminContext;
    UUID admin;
    UUID clerk;
    UUID drifter;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var init = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla5-admin@example.com", "pla5-admin"));
        admin = init.personId();
        adminContext = context(admin);
        TenantContextHolder.set(adminContext);
        repository = new InMemoryPolicyRepository();
        permissions = PermissionModule.create(repository, iam.repository(), new InMemoryPolicyInvalidation());

        Model.Department office = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        Model.Position clerkPosition = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null));
        iam.organization().createPosition(new OrganizationService.PositionCommand("vacant", "空岗", "GENERAL", null));
        clerk = person("Clerk", "C-5", "clerk5@example.com");
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                clerk, office.id(), clerkPosition.id(), Model.EmploymentKind.PRIMARY, LocalDate.now(), null));
        drifter = person("Drifter", "D-5", "drifter5@example.com"); // no employment at all
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    UUID person(String name, String no, String email) {
        return iam.organization().createPerson(new OrganizationService.PersonCommand(name, no, email, null, null, UUID.randomUUID())).id();
    }

    TenantContext context(UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    boolean may(UUID person, String resource, String action) {
        TenantContextHolder.set(context(person));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    /** Legacy seeding as it runs today: each person who shows up is assigned tenant_member personally. */
    void legacySeed(UUID... people) {
        TenantMemberGrantSeeder legacy = new TenantMemberGrantSeeder(permissions, iam.repository(), MemberAccessMode.LEGACY);
        for (UUID person : people) {
            TenantContextHolder.set(context(person));
            legacy.ensure();
        }
        TenantContextHolder.set(adminContext);
    }

    long userSeeds() {
        return repository.listAssignments(tenant).stream().filter(item -> "user".equals(item.principalType())).count();
    }

    MemberAccessConversionService service(MemberAccessMode mode) {
        return new MemberAccessConversionService(permissions, iam.repository(),
                new TenantMemberGrantSeeder(permissions, iam.repository(), mode), mode);
    }

    @Test
    void positionModeSeederDefinesRolesButAssignsNobody() {
        TenantMemberGrantSeeder seeder = new TenantMemberGrantSeeder(permissions, iam.repository(), MemberAccessMode.POSITION);
        TenantContextHolder.set(context(clerk));
        seeder.ensure();
        TenantContextHolder.set(adminContext);

        assertTrue(repository.findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).isPresent());
        assertTrue(repository.findRoleByCode(tenant, TenantMemberGrantSeeder.DESIGNER_ROLE).isPresent());
        assertEquals(0, userSeeds());
        assertFalse(may(clerk, "ledger", "read"), "no position binding yet");
    }

    @Test
    void aDryRunReportsThePlanAndWritesNothing() {
        legacySeed(admin, clerk);
        long assignments = repository.listAssignments(tenant).size();

        var plan = service(MemberAccessMode.POSITION).convert(true);

        assertTrue(plan.dryRun());
        assertTrue(plan.memberPositions().contains("clerk"));
        assertFalse(plan.memberPositions().contains("vacant"), "empty positions are not bound");
        assertEquals(1, plan.designerPositions().size(), "the tenant admin position gets the designer role");
        assertEquals(plan.memberPositions().size() + plan.designerPositions().size(), plan.bindingsAdded());
        assertTrue(plan.seedAssignmentsRemoved() >= 3, "admin member + admin designer + clerk member seeds");
        assertEquals(1, plan.personsWithoutPosition(), "the drifter would lose the member baseline");
        assertEquals(assignments, repository.listAssignments(tenant).size());
    }

    @Test
    void convertingInPositionModeKeepsAccessForPeopleWithPositionsAndIsIdempotent() {
        legacySeed(admin, clerk, drifter);
        assertTrue(may(clerk, "ledger", "read"));
        assertTrue(may(drifter, "ledger", "read"));

        var applied = service(MemberAccessMode.POSITION).convert(false);

        assertEquals(0, userSeeds(), "per-person seeds are retired");
        assertTrue(may(clerk, "ledger", "read"), "the clerk keeps access through the position");
        assertTrue(may(admin, "process.definition", "write"), "the admin keeps designing through the admin position");
        assertFalse(may(clerk, "process.definition", "write"));
        assertFalse(may(drifter, "ledger", "read"), "no position, no member baseline — as reported");
        assertEquals(1, applied.personsWithoutPosition());

        var again = service(MemberAccessMode.POSITION).convert(false);
        assertEquals(0, again.bindingsAdded());
        assertEquals(0, again.seedAssignmentsRemoved());
    }

    @Test
    void convertingInLegacyModeOnlyAddsBindings() {
        legacySeed(admin, clerk);
        long seeds = userSeeds();

        var applied = service(MemberAccessMode.LEGACY).convert(false);

        assertEquals("LEGACY", applied.mode());
        assertTrue(applied.bindingsAdded() > 0);
        assertEquals(0, applied.seedAssignmentsRemoved());
        assertEquals(seeds, userSeeds(), "legacy would re-seed anyway, so nothing is removed");
        assertTrue(may(clerk, "ledger", "read"));
    }
}
