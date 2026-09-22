package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Failing-before / passing-after proof that a platform or tenant admin
 * without MFA cannot complete a privileged session.
 */
class AdminMfaSessionTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PLATFORM = AdminMfaPolicy.DEFAULT_PLATFORM_TENANT;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void beforeGateAdminWithoutMfaCouldIssuePrivilegedContext() {
        IdentityModule module = module(AdminMfaPolicy.disabled());
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Gate Admin", "gate-admin@example.com", "gate-admin"));
        String passwordOnly = StubIdentityProvider.tokenWithoutMfa(
                "gate-admin", "gate-admin@example.com", "gate-admin", "Gate Admin");
        SessionService.SessionView view = module.sessions().loginWithAccessToken(passwordOnly);
        assertEquals(TENANT, view.tenantId());
        assertNotNull(view.contextId(), "pre-gate behavior: admin password-only token issued a context");
    }

    @Test
    void afterGateAdminWithoutMfaCannotIssuePrivilegedSession() {
        AuditPort audits = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        IdentityModule module = module(AdminMfaPolicy.forced(), audits);
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Gate Admin", "gate-admin@example.com", "gate-admin"));
        String passwordOnly = StubIdentityProvider.tokenWithoutMfa(
                "gate-admin", "gate-admin@example.com", "gate-admin", "Gate Admin");
        DomainException ex = assertThrows(DomainException.class,
                () -> module.sessions().loginWithAccessToken(passwordOnly));
        assertEquals("P2.IAM.MFA_REQUIRED", ex.code());
        assertEquals(403, ex.httpStatus());
        assertNull(TenantContextHolder.get(), "privileged TenantContext must not be bound");
        assertTrue(audits.chain().stream()
                .anyMatch(event -> AuditCaptures.LOGIN_REJECTED.equals(event.event().eventType())
                        && "P2.IAM.MFA_REQUIRED".equals(event.event().details().get("code"))));
    }

    @Test
    void afterGateAdminWithMfaIssuesPrivilegedSession() {
        IdentityModule module = module(AdminMfaPolicy.forced());
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Gate Admin", "gate-admin@example.com", "gate-admin"));
        String mfa = StubIdentityProvider.token(
                "gate-admin", "gate-admin@example.com", "gate-admin", "Gate Admin");
        SessionService.SessionView view = module.sessions().loginWithAccessToken(mfa);
        assertEquals(TENANT, view.tenantId());
        assertNotNull(view.contextId());
        assertTrue(module.sessions().authenticate(mfa).claims().mfaSatisfied());
    }

    @Test
    void nonAdminWithoutMfaStillGetsSession() {
        IdentityModule module = module(AdminMfaPolicy.forced());
        var admin = module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Gate Admin", "gate-admin@example.com", "gate-admin"));
        String memberToken = StubIdentityProvider.tokenWithoutMfa(
                "member-sub", "member@example.com", "member", "Member");
        module.sessions().loginWithAccessToken(memberToken);
        var member = module.repository().findAccountByEmail("member@example.com").orElseThrow();
        TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(
                admin.employmentId(), TENANT, admin.accountId(), admin.personId(),
                admin.keycloakUserId(), java.time.Instant.now(), java.time.Instant.now().plus(Duration.ofHours(1))));
        module.memberships().add(member.id());
        module.organization().createPerson(new OrganizationService.PersonCommand(
                "Member", "M-1", "member@example.com", null, null, member.id()));
        TenantContextHolder.clear();

        SessionService.SessionView view = module.sessions().loginWithAccessToken(memberToken);
        assertEquals(TENANT, view.tenantId());
        assertNotNull(view.contextId(), "non-admin demo seed is not forced through MFA");
    }

    @Test
    void platformAdminWithoutMfaCannotIssueOnPlatformTenant() {
        IdentityModule module = module(AdminMfaPolicy.forced());
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                PLATFORM, "Platform Admin", "platform-admin@example.com", "platform-admin"));
        var person = module.repository().findPersonByAccount(
                PLATFORM,
                module.repository().findAccountByEmail("platform-admin@example.com").orElseThrow().id())
                .orElseThrow();
        var position = module.repository().findPositionByCode(PLATFORM, "TENANT_ADMIN").orElseThrow();
        module.repository().savePosition(new one.nexus.p2.domain.Model.Position(
                position.id(), position.tenantId(), "PLATFORM_ADMIN", "Platform Admin",
                position.category(), position.duties(), position.status(),
                position.createdAt(), position.updatedAt(), position.deletedAt(), position.version() + 1));
        assertTrue(AdminPrivilege.holdsAdminRole(
                module.repository(), person.accountId(), PLATFORM, AdminMfaPolicy.forced()));

        DomainException ex = assertThrows(DomainException.class,
                () -> module.sessions().loginWithAccessToken(StubIdentityProvider.tokenWithoutMfa(
                        "platform-admin", "platform-admin@example.com", "platform-admin", "Platform Admin")));
        assertEquals("P2.IAM.MFA_REQUIRED", ex.code());
        assertNull(TenantContextHolder.get());
    }

    @Test
    void stepUpAuthorizeUrlCarriesAcrValues() {
        IdentityModule module = module(AdminMfaPolicy.forced());
        var url = module.sessions().authorizationUrl("st", "http://127.0.0.1/cb", "mfa");
        assertTrue(url.url().contains("acr_values=mfa"), url.url());
    }

    @Test
    void loadContextReChecksAdminMfaWhenClaimsArePresent() {
        IdentityModule off = module(AdminMfaPolicy.disabled());
        off.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Gate Admin", "gate-admin@example.com", "gate-admin"));
        SessionService.SessionView issued = off.sessions().loginWithAccessToken(
                StubIdentityProvider.tokenWithoutMfa(
                        "gate-admin", "gate-admin@example.com", "gate-admin", "Gate Admin"));
        assertNotNull(issued.contextId());

        IdentityModule on = module(AdminMfaPolicy.forced());
        copyAdmin(off, on);
        on.repository().saveContext(off.repository().findContext(issued.contextId()).orElseThrow());
        var passwordOnly = on.sessions().authenticate(StubIdentityProvider.tokenWithoutMfa(
                "gate-admin", "gate-admin@example.com", "gate-admin", "Gate Admin"));
        DomainException ex = assertThrows(DomainException.class,
                () -> on.sessions().loadContext(issued.contextId(), passwordOnly));
        assertEquals("P2.IAM.MFA_REQUIRED", ex.code());
    }

    private static IdentityModule module(AdminMfaPolicy policy) {
        return module(policy, null);
    }

    private static IdentityModule module(AdminMfaPolicy policy, AuditPort audits) {
        StubIdentityProvider idp = new StubIdentityProvider();
        return new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8),
                audits,
                policy);
    }

    private static void copyAdmin(IdentityModule from, IdentityModule to) {
        var account = from.repository().findAccountByEmail("gate-admin@example.com").orElseThrow();
        to.repository().saveAccount(account);
        from.repository().listMembershipsByAccount(account.id())
                .forEach(item -> to.repository().saveMembership(item));
        from.repository().listPersons(TENANT).forEach(item -> to.repository().savePerson(item));
        from.repository().listDepartments(TENANT).forEach(item -> to.repository().saveDepartment(item));
        from.repository().listPositions(TENANT).forEach(item -> to.repository().savePosition(item));
        var person = from.repository().findPersonByAccount(TENANT, account.id()).orElseThrow();
        from.repository().listEmploymentsByPerson(TENANT, person.id())
                .forEach(item -> to.repository().saveEmployment(item));
    }
}
