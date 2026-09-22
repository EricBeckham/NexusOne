package one.nexus.p2;

import one.nexus.p2.account.PersonIdentityReconciler;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonIdentityReconcileTest {
    private IdentityModule module;
    private StubIdentityProvider idp;
    private UUID tenantA;
    private UUID tenantB;
    private TenantAdminInitializer.Result adminA;
    private TenantAdminInitializer.Result adminB;

    @BeforeEach
    void setUp() {
        idp = new StubIdentityProvider();
        module = new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8));
        tenantA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        adminA = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Admin A", "admin-a@example.com", "admin-a"));
        adminB = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantB, "Admin B", "admin-b@example.com", "admin-b"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void previewMatchesProvisionedPersons() {
        PersonIdentityReconciler.Report report = module.personReconciler().reconcile(false);
        assertFalse(report.applied());
        assertEquals("identity.reconcile", report.path());
        assertEquals(2, report.matched());
        assertEquals(0, report.drifted());
        assertEquals(0, report.proposed());
        assertEquals(0, report.repaired());
        assertTrue(report.findings().stream().allMatch(item -> item.kind() == PersonIdentityReconciler.Kind.MATCHED));
    }

    @Test
    void dryRunLeavesDriftInPlaceAndApplyRepairsPersonEmail() {
        Model.Person person = module.repository().findPerson(tenantA, adminA.personId()).orElseThrow();
        Instant now = Instant.now();
        module.repository().savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                "stale-a@example.com", person.phone(), person.hiredOn(), person.status(),
                person.createdAt(), now, person.deletedAt(), person.version() + 1));

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.drifted());
        assertEquals(1, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(PersonIdentityReconciler.Action.SYNC_P2, preview.repairs().getFirst().action());
        assertTrue(preview.repairs().getFirst().detail().startsWith("would "));
        assertEquals("stale-a@example.com",
                module.repository().findPerson(tenantA, adminA.personId()).orElseThrow().email());

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true);
        assertTrue(applied.applied());
        assertEquals(1, applied.proposed());
        assertEquals(1, applied.repaired());
        assertEquals(1, applied.repairs().size());
        assertEquals(PersonIdentityReconciler.Action.SYNC_P2, applied.repairs().getFirst().action());
        assertEquals("admin-a@example.com",
                module.repository().findPerson(tenantA, adminA.personId()).orElseThrow().email());
        assertEquals("Admin A",
                module.repository().findPerson(tenantA, adminA.personId()).orElseThrow().displayName());
    }

    @Test
    void applyRebindsStaleKeycloakUserIdByEmail() {
        Model.GlobalAccount account = module.repository().findAccount(adminA.accountId()).orElseThrow();
        String original = account.keycloakUserId();
        module.repository().saveAccount(new Model.GlobalAccount(
                account.id(), "gone-sub", account.email(), account.phone(), account.username(),
                account.displayName(), account.status(), account.createdAt(), Instant.now(),
                account.deletedAt(), account.version() + 1));

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.staleBinding());
        assertEquals(1, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(PersonIdentityReconciler.Action.REBIND, preview.repairs().getFirst().action());
        assertEquals("gone-sub",
                module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId());

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true);
        assertEquals(1, applied.repaired());
        assertEquals(PersonIdentityReconciler.Action.REBIND, applied.repairs().getFirst().action());
        assertEquals(original,
                module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId());
    }

    @Test
    void applyProvisionsMissingKeycloakUser() {
        Model.GlobalAccount account = module.repository().findAccount(adminA.accountId()).orElseThrow();
        idp.removeUser(account.keycloakUserId());
        module.repository().saveAccount(new Model.GlobalAccount(
                account.id(), "ghost-sub", "ghost-a@example.com", account.phone(), account.username(),
                account.displayName(), account.status(), account.createdAt(), Instant.now(),
                account.deletedAt(), account.version() + 1));
        Model.Person person = module.repository().findPerson(tenantA, adminA.personId()).orElseThrow();
        module.repository().savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                "ghost-a@example.com", person.phone(), person.hiredOn(), person.status(),
                person.createdAt(), Instant.now(), person.deletedAt(), person.version() + 1));

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.missingIdp());
        assertEquals(1, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(PersonIdentityReconciler.Action.PROVISION_IDP, preview.repairs().getFirst().action());
        assertTrue(idp.findUser("ghost-sub").isEmpty());

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true);
        assertEquals(1, applied.repaired());
        assertEquals(PersonIdentityReconciler.Action.PROVISION_IDP, applied.repairs().getFirst().action());
        String bound = module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId();
        assertFalse(bound.isBlank());
        assertTrue(idp.findUser(bound).isPresent());
        assertEquals("ghost-a@example.com", idp.findUser(bound).orElseThrow().email());
    }

    @Test
    void unmatchedKeycloakUserIsReportedAndNotCreatedAsPerson() {
        idp.ensureUser("orphan@example.com", "orphan", "Orphan");
        int personsBefore = module.repository().listAllPersons().size();

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.unmatchedIdp());
        assertTrue(preview.findings().stream().anyMatch(item ->
                item.kind() == PersonIdentityReconciler.Kind.UNMATCHED_IDP
                        && "orphan@example.com".equals(item.idpEmail())));

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true);
        assertEquals(1, applied.unmatchedIdp());
        assertEquals(0, applied.repaired());
        assertEquals(personsBefore, module.repository().listAllPersons().size());
        assertTrue(module.repository().findAccountByEmail("orphan@example.com").isEmpty());
    }

    @Test
    void tenantScopedRepairDoesNotTouchTheOtherTenant() {
        drift(tenantA, adminA.personId(), "stale-a@example.com");
        drift(tenantB, adminB.personId(), "stale-b@example.com");

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true, tenantA);
        assertEquals(1, applied.repaired());
        assertEquals(0, applied.unmatchedIdp());
        assertEquals("admin-a@example.com",
                module.repository().findPerson(tenantA, adminA.personId()).orElseThrow().email());
        assertEquals("stale-b@example.com",
                module.repository().findPerson(tenantB, adminB.personId()).orElseThrow().email());
    }

    @Test
    void dualTenantPersonsOfOneAccountStayMatchedSeparately() {
        String token = StubIdentityProvider.token("carol-sub", "carol@example.com", "carol", "Carol");
        module.sessions().loginWithAccessToken(token);
        var carol = module.repository().findAccountByEmail("carol@example.com").orElseThrow();
        bind(tenantA, adminA);
        module.memberships().add(carol.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Carol A", "C-A", "carol@example.com", null, null, carol.id()));
        bind(tenantB, adminB);
        module.memberships().add(carol.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Carol B", "C-B", "carol@example.com", null, null, carol.id()));
        TenantContextHolder.clear();

        PersonIdentityReconciler.Report report = module.personReconciler().reconcile(false);
        long carolMatches = report.findings().stream()
                .filter(item -> carol.id().equals(item.accountId())
                        && item.kind() == PersonIdentityReconciler.Kind.MATCHED)
                .count();
        assertEquals(2, carolMatches);
    }

    @Test
    void emailCollisionAgainstAnotherAccountIsConflictNotRebound() {
        Model.GlobalAccount other = module.repository().findAccount(adminB.accountId()).orElseThrow();
        Model.GlobalAccount account = module.repository().findAccount(adminA.accountId()).orElseThrow();
        idp.removeUser(account.keycloakUserId());
        idp.putUser(new IdentityProviderPort.IdentityClaims(
                other.keycloakUserId(), account.email(), other.username(), other.displayName(), null));
        module.repository().saveAccount(new Model.GlobalAccount(
                account.id(), "stale-a-sub", account.email(), account.phone(), account.username(),
                account.displayName(), account.status(), account.createdAt(), Instant.now(),
                account.deletedAt(), account.version() + 1));

        PersonIdentityReconciler.Report applied = module.personReconciler().reconcile(true);
        assertTrue(applied.conflicts() >= 1);
        assertEquals("stale-a-sub",
                module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId());
    }

    private void drift(UUID tenantId, UUID personId, String email) {
        Model.Person person = module.repository().findPerson(tenantId, personId).orElseThrow();
        module.repository().savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                email, person.phone(), person.hiredOn(), person.status(),
                person.createdAt(), Instant.now(), person.deletedAt(), person.version() + 1));
    }

    private void bind(UUID tenantId, TenantAdminInitializer.Result admin) {
        TenantContextHolder.set(new TenantContext(
                admin.employmentId(), tenantId, admin.accountId(), admin.personId(),
                admin.keycloakUserId(), Instant.now(), Instant.now().plus(Duration.ofHours(1))));
    }
}
