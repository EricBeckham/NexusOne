package one.nexus.p2;

import one.nexus.p2.account.PersonIdentityReconciler;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G10 leftover live dry-run against Keycloak 26.5.2 on loopback :8080.
 * Opt-in {@code NEXUS_KEYCLOAK_SMOKE=1}. Never calls apply / ensureUser for the drifted person.
 */
@SpringBootTest
@ExtendWith(KeycloakAvailableCondition.class)
class LiveKeycloakIdentityDriftDryRunTest {
    private static final UUID TENANT = UUID.fromString("10101010-1010-4101-8101-101010101011");

    @DynamicPropertySource
    static void liveKeycloak(DynamicPropertyRegistry registry) {
        registry.add("nexus.iam.idp", () -> "keycloak");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.iam.smtp.enabled", () -> "false");
        registry.add("nexus.iam.platform-bootstrap-token", () -> "test-bootstrap");
        registry.add("nexus.iam.keycloak.issuer",
                () -> KeycloakAvailableCondition.env(
                        "NEXUS_KEYCLOAK_ISSUER", "http://127.0.0.1:8080/realms/nexus"));
        registry.add("nexus.iam.keycloak.client-id",
                () -> KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform"));
        registry.add("nexus.iam.keycloak.client-secret",
                () -> KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_SECRET", ""));
    }

    @Autowired
    IdentityModule module;

    @Autowired
    IdentityProviderPort idp;

    @Test
    void liveKeycloakDetectsDriftAndProposesSyncWithoutWrite() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                idp.getClass().getName().contains("Keycloak"),
                "Stub IdP must be off for live G10 drift dry-run");

        TenantAdminInitializer.Result admin = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(
                        TENANT, "G10 Dry Admin", "g10-dry-admin@example.com", "g10-dry-admin"));
        assertTrue(idp.findUser(admin.keycloakUserId()).isPresent(), "provisioned Keycloak user");

        PersonIdentityReconciler.Report matched = module.personReconciler().reconcile(false, TENANT);
        assertTrue(matched.matched() >= 1, "provisioned person must match Keycloak user");
        assertEquals(0, matched.proposed());
        assertEquals(0, matched.repaired());

        Model.Person person = module.repository().findPerson(TENANT, admin.personId()).orElseThrow();
        module.repository().savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                "g10-dry-stale@example.com", person.phone(), person.hiredOn(), person.status(),
                person.createdAt(), Instant.now(), person.deletedAt(), person.version() + 1));

        PersonIdentityReconciler.Report preview = module.personReconciler().reconcile(false, TENANT);
        assertFalse(preview.applied());
        assertTrue(preview.drifted() >= 1, preview.findings().toString());
        assertTrue(preview.proposed() >= 1, preview.repairs().toString());
        assertEquals(0, preview.repaired());
        assertTrue(preview.repairs().stream().anyMatch(item ->
                item.action() == PersonIdentityReconciler.Action.SYNC_P2
                        && item.detail().startsWith("would ")),
                preview.repairs().toString());
        assertEquals("g10-dry-stale@example.com",
                module.repository().findPerson(TENANT, admin.personId()).orElseThrow().email());
        assertEquals("g10-dry-admin@example.com",
                idp.findUser(admin.keycloakUserId()).orElseThrow().email());

        IdentityProviderPort.ProvisionedUser orphan = idp.ensureUser(
                "g10-dry-orphan@example.com", "g10-dry-orphan", "G10 Dry Orphan");
        int persons = module.repository().listAllPersons().size();
        PersonIdentityReconciler.Report unmatched = module.personReconciler().reconcile(false);
        assertTrue(unmatched.findings().stream().anyMatch(item ->
                item.kind() == PersonIdentityReconciler.Kind.UNMATCHED_IDP
                        && orphan.subject().equals(item.idpSubject())),
                unmatched.findings().toString());
        assertEquals(persons, module.repository().listAllPersons().size());
        assertEquals(0, unmatched.repaired());

        System.out.println("G10_DRIFT_DRY_RUN_APPLIED=" + preview.applied());
        System.out.println("G10_DRIFT_DRY_RUN_PROPOSED=" + preview.proposed());
        System.out.println("G10_DRIFT_DRY_RUN_REPAIRED=" + preview.repaired());
        System.out.println("G10_DRIFT_DRY_RUN_DRIFTED=" + preview.drifted());
        System.out.println("G10_DRIFT_DRY_RUN_UNMATCHED=" + unmatched.unmatchedIdp());
        System.out.println("G10_DRIFT_DRY_RUN_ISSUER="
                + KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_ISSUER", "http://127.0.0.1:8080/realms/nexus"));
    }
}
