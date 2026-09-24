package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdpConfigurationService;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdpConfigurationServiceTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void supportsTenantScopedCrudWithoutSecretFields() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        TestAdmins admins = admins(repository, clock);
        IdpConfigurationService service = new IdpConfigurationService(repository, clock, AdminMfaPolicy.forced());
        bind(admins.adminA());

        Model.IdpConfiguration created = service.create(command("Corp SSO", "client-a", "DISABLED"));
        assertEquals(1, service.list().size());
        assertEquals(created.id(), service.get(created.id()).id());
        assertEquals("client-a", created.clientId());
        assertFalse(created.toString().toLowerCase().contains("secret"));

        Model.IdpConfiguration updated = service.update(created.id(), command("Updated SSO", "client-b", "ENABLED"));
        assertEquals("Updated SSO", updated.displayName());
        assertEquals("client-b", updated.clientId());
        assertEquals(1, updated.version());

        bind(admins.adminB());
        assertEquals(0, service.list().size());
        assertNotFound(() -> service.get(created.id()));
        assertNotFound(() -> service.delete(created.id()));

        bind(admins.adminA());
        service.delete(created.id());
        assertEquals(0, service.list().size());
        assertNotFound(() -> service.get(created.id()));
    }

    @Test
    void enabledProviderRequiresIssuerAndClientId() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.systemUTC();
        TestAdmins admins = admins(repository, clock);
        IdpConfigurationService service = new IdpConfigurationService(repository, clock, AdminMfaPolicy.forced());
        bind(admins.adminA());
        DomainException error = assertThrows(DomainException.class,
                () -> service.create(command("Incomplete", null, "ENABLED")));
        assertEquals("P2.IAM.IDP_CONFIGURATION", error.code());
    }

    @Test
    void nonAdministratorCannotManageProviderMetadata() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        IdpConfigurationService service = new IdpConfigurationService(repository, Clock.systemUTC(), AdminMfaPolicy.forced());
        bind(TENANT_A);
        DomainException error = assertThrows(DomainException.class, service::list);
        assertEquals("P2.IAM.FORBIDDEN", error.code());
    }

    private static IdpConfigurationService.Command command(String name, String clientId, String status) {
        return new IdpConfigurationService.Command("OIDC", name, clientId,
                clientId == null ? null : "https://id.example.test/realms/acme", status);
    }

    private static TestAdmins admins(InMemoryIdentityRepository repository, Clock clock) {
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(repository, idp, new KeycloakSmtpHook(idp, false),
                clock, java.time.Duration.ofHours(1));
        TenantAdminInitializer.Result adminA = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(TENANT_A, "Admin A", "idp-admin-a@example.test", "idp-admin-a"));
        TenantAdminInitializer.Result adminB = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(TENANT_B, "Admin B", "idp-admin-b@example.test", "idp-admin-b"));
        return new TestAdmins(adminA, adminB);
    }

    private static void bind(TenantAdminInitializer.Result admin) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), admin.tenantId(), admin.accountId(),
                admin.personId(), admin.keycloakUserId(), now, now.plusSeconds(3600)));
    }

    private static void bind(UUID tenantId) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                UUID.randomUUID(), "non-admin", now, now.plusSeconds(3600)));
    }

    private record TestAdmins(TenantAdminInitializer.Result adminA, TenantAdminInitializer.Result adminB) {}

    private static void assertNotFound(Runnable work) {
        DomainException error = assertThrows(DomainException.class, work::run);
        assertEquals("P2.IAM.NOT_FOUND", error.code());
    }
}
