package one.nexus.p2;

import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualTenantIsolationTest {
    private IdentityModule module;
    private StubIdentityProvider idp;
    private UUID tenantA;
    private UUID tenantB;
    private TenantAdminInitializer.Result adminA;
    private TenantAdminInitializer.Result adminB;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        idp = new StubIdentityProvider();
        module = new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, true),
                Clock.systemUTC(),
                Duration.ofHours(8));
        tenantA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        adminA = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Admin A", "admin-a@example.com", "admin-a"));
        adminB = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantB, "Admin B", "admin-b@example.com", "admin-b"));

        aliceToken = StubIdentityProvider.token("alice-sub", "alice@example.com", "alice", "Alice");
        SessionService.SessionView login = module.sessions().loginWithAccessToken(aliceToken);
        assertNull(login.contextId(), "no membership yet, context must not be issued");

        var alice = module.repository().findAccountByEmail("alice@example.com").orElseThrow();
        bind(tenantA, adminA);
        module.memberships().add(alice.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Alice A", "A-001", "alice@example.com", null, null, alice.id()));

        bind(tenantB, adminB);
        module.memberships().add(alice.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Alice B", "B-001", "alice@example.com", null, null, alice.id()));
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dualTenantMembershipDoesNotLeakPersons() {
        SessionService.SessionView switched = module.sessions().switchTenant(
                module.sessions().authenticate(aliceToken), tenantA);
        assertEquals(tenantA, switched.tenantId());
        assertEquals(1, module.organization().listPersons().stream()
                .filter(person -> "Alice A".equals(person.displayName()))
                .count());
        assertTrue(module.organization().listPersons().stream()
                .noneMatch(person -> "Alice B".equals(person.displayName())));
        assertTrue(module.organization().listPersons().stream()
                .noneMatch(person -> tenantB.equals(person.tenantId())));

        module.sessions().switchTenant(module.sessions().authenticate(aliceToken), tenantB);
        assertEquals(1, module.organization().listPersons().stream()
                .filter(person -> "Alice B".equals(person.displayName()))
                .count());
        assertTrue(module.organization().listPersons().stream()
                .noneMatch(person -> "Alice A".equals(person.displayName())));
    }

    @Test
    void switchRejectsTenantWithoutMembership() {
        UUID stranger = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        DomainException ex = assertThrows(DomainException.class, () ->
                module.sessions().switchTenant(module.sessions().authenticate(aliceToken), stranger));
        assertEquals("P2.IAM.FORBIDDEN", ex.code());
    }

    @Test
    void issuedContextIsBoundToIdpSubject() {
        SessionService.SessionView aliceContext = module.sessions().switchTenant(
                module.sessions().authenticate(aliceToken), tenantA);
        String bobToken = StubIdentityProvider.token("bob-sub", "bob@example.com", "bob", "Bob");
        module.sessions().loginWithAccessToken(bobToken);
        DomainException ex = assertThrows(DomainException.class, () ->
                module.sessions().loadContext(aliceContext.contextId(), "bob-sub"));
        assertEquals("P2.IAM.FORBIDDEN", ex.code());
    }

    @Test
    void orgLookupsAreTenantScoped() {
        SessionService.SessionView inA = module.sessions().switchTenant(
                module.sessions().authenticate(aliceToken), tenantA);
        Model.Person aliceA = module.organization().listPersons().stream()
                .filter(person -> "Alice A".equals(person.displayName()))
                .findFirst()
                .orElseThrow();
        UUID personInA = aliceA.id();

        module.sessions().switchTenant(module.sessions().authenticate(aliceToken), tenantB);
        DomainException ex = assertThrows(DomainException.class, () -> module.organization().getPerson(personInA));
        assertEquals("P2.IAM.NOT_FOUND", ex.code());
        assertNotEquals(inA.tenantId(), TenantContextHolder.require().tenantId());
    }

    @Test
    void loginAutoIssuesContextWhenSingleMembership() {
        String soloToken = StubIdentityProvider.token("solo-sub", "solo@example.com", "solo", "Solo");
        module.sessions().loginWithAccessToken(soloToken);
        var solo = module.repository().findAccountByEmail("solo@example.com").orElseThrow();
        bind(tenantA, adminA);
        module.memberships().add(solo.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Solo A", "S-001", "solo@example.com", null, null, solo.id()));
        TenantContextHolder.clear();

        SessionService.SessionView session = module.sessions().loginWithAccessToken(soloToken);
        assertEquals(tenantA, session.tenantId());
        assertNotNull(session.contextId());
        TenantContext context = TenantContextHolder.require();
        assertEquals(tenantA, context.tenantId());
        assertEquals(solo.id(), context.accountId());
    }

    @Test
    void departmentTreeAndEmploymentStayInsideTenant() {
        module.sessions().switchTenant(module.sessions().authenticate(aliceToken), tenantA);
        Model.Person aliceA = module.organization().listPersons().stream()
                .filter(person -> "Alice A".equals(person.displayName()))
                .findFirst()
                .orElseThrow();
        Model.Department eng = module.organization().createDepartment(
                new OrganizationService.DepartmentCommand("ENG", "Engineering", adminA.departmentId(), 1, aliceA.id()));
        assertTrue(eng.path().startsWith(module.repository().findDepartment(tenantA, adminA.departmentId()).orElseThrow().path()));
        Model.Position engineer = module.organization().createPosition(
                new OrganizationService.PositionCommand("ENG", "Engineer", "ic", "Build"));
        Model.Employment employment = module.organization().assign(new OrganizationService.EmploymentCommand(
                aliceA.id(), eng.id(), engineer.id(), Model.EmploymentKind.SECONDARY, null, null));
        assertEquals(tenantA, employment.tenantId());

        module.sessions().switchTenant(module.sessions().authenticate(aliceToken), tenantB);
        assertTrue(module.organization().departmentTree().stream()
                .noneMatch(node -> "ENG".equals(node.department().code())));
        assertTrue(module.organization().listPositions().stream()
                .noneMatch(position -> "Engineer".equals(position.name())));
    }

    @Test
    void smtpHooksAreRecordedWhenEnabled() {
        module.sessions().switchTenant(module.sessions().authenticate(aliceToken), tenantA);
        Model.Person aliceA = module.organization().listPersons().stream()
                .filter(person -> "Alice A".equals(person.displayName()))
                .findFirst()
                .orElseThrow();
        Model.GlobalAccount account = module.repository().findAccount(aliceA.accountId()).orElseThrow();
        module.smtp().invite(account.keycloakUserId());
        module.smtp().resetPassword(account.keycloakUserId());
        assertEquals(2, idp.sentEmails().size());
        assertEquals("invite", idp.sentEmails().get(0).kind());
        assertEquals("password-reset", idp.sentEmails().get(1).kind());
    }

    @Test
    void missingContextRejectsOrgWrites() {
        DomainException ex = assertThrows(DomainException.class, () ->
                module.organization().createDepartment(
                        new OrganizationService.DepartmentCommand("X", "X", null, 0, null)));
        assertEquals("P2.IAM.UNAUTHORIZED", ex.code());
    }

    private void bind(UUID tenantId, TenantAdminInitializer.Result admin) {
        TenantContextHolder.set(new TenantContext(
                admin.employmentId(), tenantId, admin.accountId(), admin.personId(),
                admin.keycloakUserId(), java.time.Instant.now(), java.time.Instant.now().plus(Duration.ofHours(1))));
    }
}
