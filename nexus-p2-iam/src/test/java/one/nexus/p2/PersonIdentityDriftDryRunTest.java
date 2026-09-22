package one.nexus.p2;

import one.nexus.p2.account.PersonIdentityReconciler;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
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

/**
 * G10 leftover: detect + proposed-fix dry-run. Default path never writes P2 or IdP.
 */
class PersonIdentityDriftDryRunTest {
    private IdentityModule module;
    private StubIdentityProvider idp;
    private UUID tenantA;
    private TenantAdminInitializer.Result adminA;

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
        adminA = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Admin A", "admin-a@example.com", "admin-a"));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                "Admin B", "admin-b@example.com", "admin-b"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dryRunProposesEmailSyncAndLeavesPersonUnchanged() {
        Model.Person person = module.repository().findPerson(tenantA, adminA.personId()).orElseThrow();
        driftEmail(person, "stale-a@example.com");

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertFalse(preview.applied());
        assertEquals("identity.reconcile", preview.path());
        assertEquals(1, preview.drifted());
        assertEquals(1, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(PersonIdentityReconciler.Action.SYNC_P2, preview.repairs().getFirst().action());
        assertTrue(preview.repairs().getFirst().detail().startsWith("would "));
        assertEquals("stale-a@example.com",
                module.repository().findPerson(tenantA, adminA.personId()).orElseThrow().email());
        assertEquals("admin-a@example.com",
                module.repository().findAccount(adminA.accountId()).orElseThrow().email());
    }

    @Test
    void dryRunProposesRebindAndLeavesStaleSubject() {
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
        assertTrue(idp.findUser(original).isPresent());
    }

    @Test
    void dryRunProposesProvisionWithoutCallingEnsureUser() {
        Model.GlobalAccount account = module.repository().findAccount(adminA.accountId()).orElseThrow();
        idp.removeUser(account.keycloakUserId());
        module.repository().saveAccount(new Model.GlobalAccount(
                account.id(), "ghost-sub", "ghost-a@example.com", account.phone(), account.username(),
                account.displayName(), account.status(), account.createdAt(), Instant.now(),
                account.deletedAt(), account.version() + 1));
        Model.Person person = module.repository().findPerson(tenantA, adminA.personId()).orElseThrow();
        driftEmail(person, "ghost-a@example.com");

        int idpUsers = idp.listUsers().size();
        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.missingIdp());
        assertEquals(1, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(PersonIdentityReconciler.Action.PROVISION_IDP, preview.repairs().getFirst().action());
        assertTrue(idp.findUser("ghost-sub").isEmpty());
        assertEquals(idpUsers, idp.listUsers().size());
        assertEquals("ghost-sub",
                module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId());
    }

    @Test
    void dryRunReportsUnmatchedIdpWithoutAProposedWrite() {
        idp.ensureUser("orphan@example.com", "orphan", "Orphan");
        int personsBefore = module.repository().listAllPersons().size();

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertEquals(1, preview.unmatchedIdp());
        assertEquals(0, preview.proposed());
        assertEquals(0, preview.repaired());
        assertEquals(personsBefore, module.repository().listAllPersons().size());
        assertTrue(module.repository().findAccountByEmail("orphan@example.com").isEmpty());
    }

    @Test
    void dryRunConflictProposesNothingAndDoesNotRebind() {
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        var adminB = module.repository().findAccountByEmail("admin-b@example.com").orElseThrow();
        Model.GlobalAccount other = module.repository().findAccount(adminB.id()).orElseThrow();
        Model.GlobalAccount account = module.repository().findAccount(adminA.accountId()).orElseThrow();
        idp.removeUser(account.keycloakUserId());
        idp.putUser(new IdentityProviderPort.IdentityClaims(
                other.keycloakUserId(), account.email(), other.username(), other.displayName(), null));
        module.repository().saveAccount(new Model.GlobalAccount(
                account.id(), "stale-a-sub", account.email(), account.phone(), account.username(),
                account.displayName(), account.status(), account.createdAt(), Instant.now(),
                account.deletedAt(), account.version() + 1));

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false);
        assertTrue(preview.conflicts() >= 1);
        assertEquals(0, preview.repaired());
        assertTrue(preview.repairs().stream().noneMatch(item ->
                adminA.accountId().equals(item.accountId())));
        assertEquals("stale-a-sub",
                module.repository().findAccount(adminA.accountId()).orElseThrow().keycloakUserId());
        assertEquals(tenantB, module.repository().findPersonByAccount(tenantB, adminB.id()).orElseThrow().tenantId());
    }

    private void driftEmail(Model.Person person, String email) {
        module.repository().savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                email, person.phone(), person.hiredOn(), person.status(),
                person.createdAt(), Instant.now(), person.deletedAt(), person.version() + 1));
    }
}
