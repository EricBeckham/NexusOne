package one.nexus.permission.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.HashChainVerification;
import one.nexus.common.audit.AuditEvent;
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
import one.nexus.permission.pack.RolePackService.AppliedRole;
import one.nexus.permission.pack.RolePackService.GrantSpec;
import one.nexus.permission.pack.RolePackService.RolePackCommand;
import one.nexus.permission.pack.RolePackService.RoleSpec;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.position.PositionRoleService;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolePackServiceTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a07");
    final RecordingAudit audit = new RecordingAudit();
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
                tenant, "Admin", "pla7-admin@example.com", "pla7-admin"));
        adminContext = context(tenant, admin.personId());
        TenantContextHolder.set(adminContext);
        repository = new InMemoryPolicyRepository();
        permissions = PermissionModule.create(repository, iam.repository(), new InMemoryPolicyInvalidation(), audit);

        Model.Position position = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null));
        clerkPosition = position.id();
        Model.Department department = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        Model.Person person = iam.organization().createPerson(
                new OrganizationService.PersonCommand("Clerk", "C-7", "clerk7@example.com", null, null, null));
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                person.id(), department.id(), position.id(), Model.EmploymentKind.PRIMARY, LocalDate.now(), null));
        clerk = person.id();
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    static TenantContext context(UUID tenant, UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    static RolePackCommand leave(String version, GrantSpec... applicantGrants) {
        return new RolePackCommand("leave", version, List.of(
                new RoleSpec("leave_applicant", "请假申请人", List.of(applicantGrants)),
                new RoleSpec("leave_approver", "请假审批人", List.of(
                        new GrantSpec("fn.leave.leave_view", "read", "DEPT_AND_CHILDREN")))));
    }

    static GrantSpec g(String resource, String action) {
        return new GrantSpec(resource, action, "SELF");
    }

    boolean clerkMay(String resource, String action) {
        TenantContextHolder.set(context(tenant, clerk));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    UUID roleId(String code) {
        return repository.findRoleByCode(tenant, code).orElseThrow().id();
    }

    @Test
    void landsPackRolesWithoutBindingThemToAnyone() {
        var result = permissions.rolePacks().apply(
                leave("1.0.0", g("fn.leave.leave_apply", "read"), g("fn.leave.leave_apply", "write")));

        assertEquals(List.of(new AppliedRole("app_leave_leave_applicant", true, 2, List.of()),
                new AppliedRole("app_leave_leave_approver", true, 1, List.of())), result.roles());
        assertTrue(repository.findRoleByCode(tenant, "app_leave_leave_applicant").orElseThrow().systemRole());
        assertFalse(clerkMay("fn.leave.leave_apply", "read"), "D4: landing does not bind positions");

        new PositionRoleService(permissions, iam.repository()).set(clerkPosition, List.of(roleId("app_leave_leave_applicant")));
        assertTrue(clerkMay("fn.leave.leave_apply", "read"));
        assertTrue(clerkMay("fn.leave.leave_apply", "write"));

        List<AuditEvent> packAudits = audit.events.stream().filter(e -> "role_pack".equals(e.resourceType())).toList();
        assertEquals(1, packAudits.size(), "one audit per landing (the position binding audits separately)");
        assertEquals("1.0.0", packAudits.getFirst().details().get("app_version"));
    }

    @Test
    void reapplyingIsANoOpAndDoesNotRepublishThePolicy() {
        permissions.rolePacks().apply(leave("1.0.0", g("fn.leave.leave_apply", "read")));
        long version = repository.currentVersion(tenant);
        int grants = repository.listGrants(tenant).size();

        var again = permissions.rolePacks().apply(leave("1.0.0", g("fn.leave.leave_apply", "read")));

        assertTrue(again.roles().stream().noneMatch(role -> role.created() || role.grantsAdded() > 0));
        assertEquals(version, repository.currentVersion(tenant));
        assertEquals(grants, repository.listGrants(tenant).size());
    }

    @Test
    void anUpgradeAddsNewGrantsButOnlyReportsDroppedOnes() {
        permissions.rolePacks().apply(leave("1.0.0", g("fn.leave.leave_apply", "read"), g("fn.leave.leave_apply", "write")));
        new PositionRoleService(permissions, iam.repository()).set(clerkPosition, List.of(roleId("app_leave_leave_applicant")));

        var upgraded = permissions.rolePacks().apply(leave("1.1.0", g("fn.leave.leave_apply", "read"), g("fn.leave.leave_view", "read")));

        AppliedRole applicant = upgraded.roles().getFirst();
        assertFalse(applicant.created());
        assertEquals(1, applicant.grantsAdded());
        assertEquals(List.of("fn.leave.leave_apply:write:SELF"), applicant.grantsNotInPack());
        assertTrue(clerkMay("fn.leave.leave_view", "read"), "new grant applies at once");
        assertTrue(clerkMay("fn.leave.leave_apply", "write"), "dropped grant is not revoked automatically");
    }

    @Test
    void invalidPacksAreRefusedBeforeAnyWrite() {
        List<RolePackCommand> bad = List.of(
                leave("1.0.0", g("permission.role", "write")),                  // not its own entry
                leave("1.0.0", g("fn.expense.expense_apply", "read")),          // another app's entry
                leave("1.0.0", g("fn.leave.leave_apply", "approve")),           // unknown action
                leave("1.0.0", new GrantSpec("fn.leave.leave_apply", "read", "SPECIFIED")),
                leave("1.0", g("fn.leave.leave_apply", "read")),
                new RolePackCommand("Leave", "1.0.0", List.of()),
                new RolePackCommand("leave", "1.0.0", List.of(
                        new RoleSpec("dup", "a", List.of()), new RoleSpec("dup", "b", List.of()))));
        for (RolePackCommand command : bad) {
            assertThrows(PermissionException.class, () -> permissions.rolePacks().apply(command), command.toString());
        }
        assertTrue(repository.findRoleByCode(tenant, "app_leave_leave_applicant").isEmpty());
        assertTrue(repository.findRoleByCode(tenant, "app_leave_dup").isEmpty());
        assertTrue(repository.listGrants(tenant).isEmpty());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void appPrefixedCodesAreReservedForPacks() {
        assertThrows(PermissionException.class, () -> permissions.admin().createRole("app_leave_x", "手工", false));
        // A pre-existing hand-made role with a pack code is never taken over.
        repository.saveRole(new PolicyRepository.RoleRecord(UUID.randomUUID(), tenant, "app_leave_leave_applicant",
                "旧手工角色", false, "ACTIVE"));
        assertThrows(PermissionException.class,
                () -> permissions.rolePacks().apply(leave("1.0.0", g("fn.leave.leave_apply", "read"))));
    }

    @Test
    void anApprovedActivationWritesOnlyTheNamedTenantWithoutAContext() {
        UUID target = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        TenantContextHolder.clear();
        FakeApproval approval = new FakeApproval(target, operator);

        var result = permissions.rolePacks().applyApproved(approval, leave("1.0.0", g("fn.leave.leave_apply", "read")));

        assertTrue(result.roles().getFirst().created());
        assertTrue(repository.findRoleByCode(target, "app_leave_leave_applicant").isPresent());
        assertTrue(repository.findRoleByCode(tenant, "app_leave_leave_applicant").isEmpty());
        AuditEvent event = audit.events.getFirst();
        assertEquals(target, event.tenantId());
        assertEquals(operator, event.actorSubjectId());
        assertEquals("platform_approved", event.details().get("path"));

        approval.active = false;
        assertThrows(SecurityException.class,
                () -> permissions.rolePacks().applyApproved(approval, leave("1.1.0", g("fn.leave.leave_view", "read"))));
        assertThrows(PermissionException.class, () -> permissions.rolePacks().apply(leave("1.0.0")), "tenant path needs a context");
    }

    static final class FakeApproval implements ApprovedTenantWrite {
        final UUID tenant;
        final UUID actor;
        boolean active = true;

        FakeApproval(UUID tenant, UUID actor) {
            this.tenant = tenant;
            this.actor = actor;
        }

        @Override public UUID targetTenantId() { return tenant; }
        @Override public UUID actorId() { return actor; }
        @Override public String traceId() { return "trace-pla7"; }
        @Override public void assertActive() {
            if (!active) throw new SecurityException("capability outside its issuing request");
        }
    }

    static final class RecordingAudit implements AuditPort {
        final List<AuditEvent> events = new ArrayList<>();

        @Override public ChainedAuditEvent append(AuditEvent event) { events.add(event); return null; }
        @Override public ChainedAuditEvent appendNewTransaction(AuditEvent event) { events.add(event); return null; }
        @Override public List<ChainedAuditEvent> findByTraceId(String traceId) { return List.of(); }
        @Override public List<ChainedAuditEvent> findByCorrelationId(String correlationId) { return List.of(); }
        @Override public Optional<ChainedAuditEvent> findByEventId(UUID eventId) { return Optional.empty(); }
        @Override public List<ChainedAuditEvent> chain() { return List.of(); }
        @Override public HashChainVerification verifyChain() { return null; }
    }
}
