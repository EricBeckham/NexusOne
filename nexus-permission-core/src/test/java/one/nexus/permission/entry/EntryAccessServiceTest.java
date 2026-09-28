package one.nexus.permission.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.pack.RolePackService;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.position.PositionRoleService;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntryAccessServiceTest {
    static final String APPLY = "fn:leave:leave_apply";
    static final String VIEW = "fn:leave:leave_view";
    static final String EXPENSE = "fn:expense:expense_apply";

    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a06");
    IdentityModule iam;
    PermissionModule permissions;
    TenantContext adminContext;
    UUID clerkPosition;
    UUID clerk;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var admin = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla6-admin@example.com", "pla6-admin"));
        adminContext = context(tenant, admin.personId());
        TenantContextHolder.set(adminContext);
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(),
                new InMemoryPolicyInvalidation());
        permissions.entries().useMemberAccessMode(MemberAccessMode.POSITION); // strict filtering; LEGACY has its own test

        Model.Position position = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null));
        clerkPosition = position.id();
        Model.Department department = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        Model.Person person = iam.organization().createPerson(
                new OrganizationService.PersonCommand("Clerk", "C-6", "clerk6@example.com", null, null, null));
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                person.id(), department.id(), position.id(), Model.EmploymentKind.PRIMARY, LocalDate.now(), null));
        clerk = person.id();

        // The leave app's applicant role, bound to the clerk position (PLA1).
        permissions.rolePacks().apply(new RolePackService.RolePackCommand("leave", "1.0.0", List.of(
                new RolePackService.RoleSpec("leave_applicant", "请假申请人", List.of(
                        new RolePackService.GrantSpec("fn.leave.leave_apply", "read", "SELF"))))));
        UUID applicant = permissions.repository().findRoleByCode(tenant, "app_leave_leave_applicant").orElseThrow().id();
        new PositionRoleService(permissions, iam.repository()).set(clerkPosition, List.of(applicant));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    static TenantContext context(UUID tenant, UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    void grant(String effect, String principalType, String principalId, String resource) {
        permissions.admin().grant(effect, principalType, principalId, resource, "read", DataScopeKind.ALL, List.of(), Map.of());
    }

    Set<String> clerkReads(List<String> candidates) {
        TenantContextHolder.set(context(tenant, clerk));
        try {
            return permissions.entries().readableEntries(candidates);
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    @Test
    void positionRolesDecideWhichInstalledEntriesAMemberSees() {
        assertEquals(Set.of(APPLY), clerkReads(List.of(APPLY, VIEW, EXPENSE)));
        assertTrue(clerkReads(List.of(VIEW, EXPENSE)).isEmpty(), "not installed or not granted → not shown");
    }

    @Test
    void aPersonalExceptionGrantAddsAnEntryAndAnExplicitDenyWins() {
        grant("GRANT", "user", clerk.toString(), "fn.leave.leave_view");
        assertEquals(List.of(APPLY, VIEW), List.copyOf(clerkReads(List.of(APPLY, VIEW))));

        grant("DENY", "user", clerk.toString(), "fn.leave.leave_apply");
        assertEquals(Set.of(VIEW), clerkReads(List.of(APPLY, VIEW)));
    }

    @Test
    void legacyModeShowsEveryInstalledEntryUnlessExplicitlyDenied() {
        permissions.entries().useMemberAccessMode(MemberAccessMode.LEGACY);
        assertEquals(List.of(APPLY, VIEW, EXPENSE), List.copyOf(clerkReads(List.of(APPLY, VIEW, EXPENSE))));

        grant("DENY", "user", clerk.toString(), "fn.expense.expense_apply");
        assertEquals(List.of(APPLY, VIEW), List.copyOf(clerkReads(List.of(APPLY, VIEW, EXPENSE))));

        permissions.entries().usePlatformTenant(tenant);
        assertTrue(clerkReads(List.of(APPLY)).isEmpty(), "the platform tenant still gets nothing");
    }

    @Test
    void endingTheEmploymentRemovesThePositionEntries() {
        var employment = iam.organization().listEmployments(clerk).getFirst();
        iam.organization().endEmployment(employment.id());
        assertTrue(clerkReads(List.of(APPLY)).isEmpty());
    }

    @Test
    void malformedKeysDuplicatesAndNullAreHandledQuietly() {
        assertEquals(List.of(APPLY), List.copyOf(clerkReads(List.of(APPLY, APPLY, "fn:Leave:x", "fn:leave", "fn.leave.leave_apply", ""))));
        assertTrue(clerkReads(null).isEmpty());
        assertNull(EntryAccessService.resourceOf("fn:leave:leave_apply:extra"));
        assertEquals("fn.leave.leave_apply", EntryAccessService.resourceOf(APPLY));
    }

    @Test
    void thePlatformTenantGetsNoBusinessEntriesAndAMissingContextIsRefused() {
        permissions.entries().usePlatformTenant(tenant);
        assertTrue(clerkReads(List.of(APPLY)).isEmpty());
        permissions.entries().usePlatformTenant(null);

        TenantContextHolder.clear();
        assertThrows(PermissionException.class, () -> permissions.entries().readableEntries(List.of(APPLY)));
    }
}
