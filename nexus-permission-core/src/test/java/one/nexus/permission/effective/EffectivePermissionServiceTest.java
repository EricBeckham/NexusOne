package one.nexus.permission.effective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import one.nexus.permission.effective.EffectivePermissionService.Effective;
import one.nexus.permission.effective.EffectivePermissionService.Permission;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.position.PositionRoleService;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectivePermissionServiceTest {
    final UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0a03");
    IdentityModule iam;
    PermissionModule permissions;
    EffectivePermissionService preview;
    TenantContext adminContext;
    UUID clerkPosition;
    UUID clerk;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(1));
        var admin = iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Admin", "pla3-admin@example.com", "pla3-admin"));
        adminContext = context(admin.personId());
        TenantContextHolder.set(adminContext);
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(), new InMemoryPolicyInvalidation());
        preview = new EffectivePermissionService(permissions, iam.repository());

        Model.Department office = iam.organization().createDepartment(
                new OrganizationService.DepartmentCommand("office", "办公室", null, 0, null));
        clerkPosition = iam.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null)).id();
        clerk = iam.organization().createPerson(new OrganizationService.PersonCommand(
                "Clerk", "C-3", "clerk3@example.com", null, null, UUID.randomUUID())).id();
        iam.organization().assign(new OrganizationService.EmploymentCommand(
                clerk, office.id(), clerkPosition, Model.EmploymentKind.PRIMARY, LocalDate.now(), null));

        // ledger_reader ← ledger_base (inherited); bound to the clerk position.
        permissions.admin().createRole("ledger_base", "台账基础", false);
        UUID reader = permissions.admin().createRole("ledger_reader", "台账查看", false).id();
        permissions.admin().inherit(reader, permissions.repository().findRoleByCode(tenant, "ledger_base").orElseThrow().id());
        grant("GRANT", "role", "ledger_reader", "ledger", "read", DataScopeKind.DEPT);
        grant("GRANT", "role", "ledger_base", "form", "read", DataScopeKind.ALL);
        new PositionRoleService(permissions, iam.repository()).set(clerkPosition, List.of(reader));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    TenantContext context(UUID person) {
        return new TenantContext(UUID.randomUUID(), tenant, null, person, "sub", Instant.now(), Instant.now().plusSeconds(600));
    }

    void grant(String effect, String type, String id, String resource, String action, DataScopeKind scope) {
        permissions.admin().grant(effect, type, id, resource, action, scope, List.of(), Map.of());
    }

    static Permission find(Effective effective, String resource, String action) {
        return effective.permissions().stream()
                .filter(p -> p.resource().equals(resource) && p.action().equals(action)).findFirst().orElseThrow();
    }

    boolean engineAllows(UUID person, String resource, String action) {
        TenantContextHolder.set(context(person));
        try {
            return permissions.service().decide(resource, action).allowed();
        } finally {
            TenantContextHolder.set(adminContext);
        }
    }

    @Test
    void aPersonSeesPositionAndInheritedRolesWithTheirSources() {
        Effective effective = preview.forPerson(clerk);

        assertEquals("PERSON", effective.subjectType());
        var reader = effective.roles().stream().filter(r -> r.roleCode().equals("ledger_reader")).findFirst().orElseThrow();
        assertEquals(EffectivePermissionService.VIA_POSITION, reader.via());
        assertEquals("文员", reader.viaLabel());
        var base = effective.roles().stream().filter(r -> r.roleCode().equals("ledger_base")).findFirst().orElseThrow();
        assertEquals(EffectivePermissionService.VIA_INHERITED, base.via());
        assertEquals("ledger_reader", base.viaId());

        Permission ledger = find(effective, "ledger", "read");
        assertEquals("ALLOW", ledger.outcome());
        assertEquals("DEPT", ledger.sources().getFirst().scope());
        assertEquals("ALLOW", find(effective, "form", "read").outcome());
        assertFalse(effective.dutyConflict());
    }

    @Test
    void personalExceptionsAreMarkedAndAnExplicitDenyWins() {
        grant("GRANT", "user", clerk.toString(), "report", "read", DataScopeKind.SELF);
        grant("DENY", "user", clerk.toString(), "ledger", "read", DataScopeKind.ALL);

        Effective effective = preview.forPerson(clerk);

        Permission report = find(effective, "report", "read");
        assertEquals("ALLOW", report.outcome());
        assertEquals("个人（例外授权）", report.sources().getFirst().label());
        Permission ledger = find(effective, "ledger", "read");
        assertEquals("DENY", ledger.outcome());
        assertEquals(2, ledger.sources().size(), "shows both the role grant and the personal deny");
    }

    @Test
    void thePreviewAgreesWithTheEngine() {
        grant("DENY", "user", clerk.toString(), "form", "read", DataScopeKind.ALL);
        grant("GRANT", "position", clerkPosition.toString(), "process.task", "approve", DataScopeKind.ALL);
        for (Permission permission : preview.forPerson(clerk).permissions()) {
            assertEquals(engineAllows(clerk, permission.resource(), permission.action()), "ALLOW".equals(permission.outcome()),
                    permission.resource() + ":" + permission.action());
        }
    }

    @Test
    void aPositionPreviewShowsOnlyWhatThePositionBrings() {
        grant("GRANT", "user", clerk.toString(), "report", "read", DataScopeKind.SELF);

        Effective effective = preview.forPosition(clerkPosition);

        assertEquals("POSITION", effective.subjectType());
        assertTrue(effective.roles().stream().allMatch(r -> !EffectivePermissionService.VIA_PERSONAL.equals(r.via())));
        assertEquals("ALLOW", find(effective, "ledger", "read").outcome());
        assertTrue(effective.permissions().stream().noneMatch(p -> p.resource().equals("report")), "personal grants excluded");
    }

    @Test
    void conflictingDutiesDenyEverythingAndUnknownSubjectsAreRefused() {
        permissions.duties().install();
        UUID system = permissions.repository().findRoleByCode(tenant, PlatformDutyCatalog.SYSTEM_ADMIN).orElseThrow().id();
        UUID audit = permissions.repository().findRoleByCode(tenant, PlatformDutyCatalog.AUDIT_ADMIN).orElseThrow().id();
        // Bypass the binding guard to model a conflict that already exists in stored data.
        permissions.repository().saveAssignment(new one.nexus.permission.persist.PolicyRepository.AssignmentRecord(
                UUID.randomUUID(), tenant, "position", clerkPosition.toString(), system, PlatformDutyCatalog.SYSTEM_ADMIN));
        permissions.repository().saveAssignment(new one.nexus.permission.persist.PolicyRepository.AssignmentRecord(
                UUID.randomUUID(), tenant, "position", clerkPosition.toString(), audit, PlatformDutyCatalog.AUDIT_ADMIN));

        Effective effective = preview.forPerson(clerk);
        assertTrue(effective.dutyConflict());
        assertTrue(effective.permissions().stream().allMatch(p -> "DENY".equals(p.outcome())));

        assertThrows(PermissionException.class, () -> preview.forPerson(UUID.randomUUID()));
        assertThrows(PermissionException.class, () -> preview.forPosition(UUID.randomUUID()));
        TenantContextHolder.clear();
        assertThrows(PermissionException.class, () -> preview.forPerson(clerk));
    }
}
