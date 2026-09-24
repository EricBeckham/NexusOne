package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdpSyncService;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdpSyncServiceTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void oidcAndKeycloakSyncStoreSuccessMetadataWithoutPasswords() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
        TenantAdminInitializer.Result admin = admin(repository, clock, TENANT_A, "sync-a");
        bind(admin);
        Model.IdpConfiguration oidc = repository.saveIdpConfiguration(provider(TENANT_A, "OIDC"));
        AtomicInteger calls = new AtomicInteger();
        IdpSyncService service = new IdpSyncService(repository, clock, AdminMfaPolicy.forced(), tenantId -> {
            assertEquals(TENANT_A, tenantId);
            calls.incrementAndGet();
        });

        IdpSyncService.SyncResult result = service.sync(oidc.id());

        assertEquals("SUCCEEDED", result.status());
        assertEquals(Boolean.TRUE, result.lastSyncOk());
        assertEquals(0, result.syncFailCount());
        assertEquals(clock.instant(), result.lastSyncAt());
        assertEquals(1, calls.get());
        assertFalse(result.toString().toLowerCase().contains("password"));

        Model.IdpConfiguration keycloak = repository.saveIdpConfiguration(provider(TENANT_A, "KEYCLOAK"));
        assertEquals("SUCCEEDED", service.sync(keycloak.id()).status());
        assertEquals(2, calls.get());
    }

    @Test
    void aFailedSyncIncrementsTheFailureCountAndDropsTheCause() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
        bind(admin(repository, clock, TENANT_A, "sync-fail"));
        Model.IdpConfiguration oidc = repository.saveIdpConfiguration(provider(TENANT_A, "OIDC"));
        IdpSyncService service = new IdpSyncService(repository, clock, AdminMfaPolicy.forced(),
                tenantId -> {
                    throw new IllegalStateException("password=must-not-leak");
                });

        IdpSyncService.SyncResult result = service.sync(oidc.id());
        Model.IdpConfiguration saved = repository.findIdpConfiguration(TENANT_A, oidc.id()).orElseThrow();

        assertEquals("FAILED", result.status());
        assertEquals(Boolean.FALSE, result.lastSyncOk());
        assertEquals(1, result.syncFailCount());
        assertEquals(1, saved.syncFailCount());
        assertFalse(result.toString().contains("must-not-leak"));
        assertFalse(saved.toString().contains("must-not-leak"));
    }

    @Test
    void anotherTenantAndANonAdminCannotSync() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.systemUTC();
        TenantAdminInitializer.Result adminA = admin(repository, clock, TENANT_A, "sync-owner");
        TenantAdminInitializer.Result adminB = admin(repository, clock, TENANT_B, "sync-other");
        bind(adminA);
        Model.IdpConfiguration oidc = repository.saveIdpConfiguration(provider(TENANT_A, "OIDC"));
        AtomicInteger calls = new AtomicInteger();
        IdpSyncService service = new IdpSyncService(repository, clock, AdminMfaPolicy.forced(),
                tenantId -> calls.incrementAndGet());

        bind(adminB);
        DomainException missing = assertThrows(DomainException.class, () -> service.sync(oidc.id()));
        assertEquals("P2.IAM.NOT_FOUND", missing.code());

        bind(TENANT_A);
        DomainException forbidden = assertThrows(DomainException.class, () -> service.sync(oidc.id()));
        assertEquals("P2.IAM.FORBIDDEN", forbidden.code());
        assertEquals(0, calls.get());
        assertTrue(repository.findIdpConfiguration(TENANT_A, oidc.id()).orElseThrow().lastSyncAt() == null);
    }

    @Test
    void unsupportedProvidersAreRejectedBeforeSync() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        Clock clock = Clock.systemUTC();
        bind(admin(repository, clock, TENANT_A, "sync-saml"));
        Model.IdpConfiguration saml = repository.saveIdpConfiguration(provider(TENANT_A, "SAML"));
        AtomicInteger calls = new AtomicInteger();
        IdpSyncService service = new IdpSyncService(repository, clock, AdminMfaPolicy.forced(),
                tenantId -> calls.incrementAndGet());

        DomainException rejected = assertThrows(DomainException.class, () -> service.sync(saml.id()));

        assertEquals("P2.IAM.IDP_PROVIDER_UNSUPPORTED", rejected.code());
        assertEquals(0, calls.get());
        assertTrue(repository.findIdpConfiguration(TENANT_A, saml.id()).orElseThrow().lastSyncAt() == null);
    }

    private static Model.IdpConfiguration provider(UUID tenantId, String type) {
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        return new Model.IdpConfiguration(UUID.randomUUID(), tenantId, type, "Directory", "client",
                "https://id.example.test/realms/acme", "ENABLED", null, null, 0, null, now, now, 0);
    }

    private static TenantAdminInitializer.Result admin(
            InMemoryIdentityRepository repository, Clock clock, UUID tenantId, String name) {
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(repository, idp, new KeycloakSmtpHook(idp, false),
                clock, Duration.ofHours(1));
        return module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(tenantId, name, name + "@example.test", name));
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
}
