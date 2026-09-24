package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.crypto.LocalEncryptedSecretAdapter;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdpConfigurationService;
import one.nexus.p2.idp.IdpSecretService;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdpSecretServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void encryptedSecretCanBeWrittenRotatedAndTestedWithoutEverBeingReturnedOrAudited() {
        Clock clock = Clock.systemUTC();
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(repository, idp, new KeycloakSmtpHook(idp, false),
                clock, Duration.ofHours(1));
        TenantAdminInitializer.Result admin = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(TENANT, "IdP Admin", "idp-secret-admin@example.test", "idp-secret-admin"));
        TenantAdminInitializer.Result otherAdmin = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        "Other Admin", "idp-secret-other@example.test", "idp-secret-other"));
        bind(admin);

        IdpConfigurationService metadata = new IdpConfigurationService(repository, clock, AdminMfaPolicy.forced());
        Model.IdpConfiguration provider = metadata.create(new IdpConfigurationService.Command(
                "OIDC", "Corp SSO", "corp-client", "https://id.example.test/realms/corp", "DISABLED"));
        var crypto = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        var port = new LocalEncryptedSecretAdapter(crypto);
        var audits = new InMemoryHashChainedAuditPort(crypto);
        AtomicReference<byte[]> testedBytes = new AtomicReference<>();
        IdpSecretService service = new IdpSecretService(repository, port, audits, clock,
                AdminMfaPolicy.forced(), (configuration, secret) -> {
                    testedBytes.set(secret);
                    return Arrays.equals("secret-v2".getBytes(StandardCharsets.UTF_8), secret);
                });

        bind(otherAdmin);
        DomainException crossTenant = assertThrows(DomainException.class, () -> service.status(provider.id()));
        assertEquals("P2.IAM.NOT_FOUND", crossTenant.code());
        assertTrue(port.find(IdpSecretService.secretName(provider)).isEmpty());
        bind(admin);

        IdpSecretService.SecretWriteResult first = service.write(provider.id(), "secret-v1");
        assertFalse(first.rotated());
        assertTrue(first.configured());
        IdpSecretService.SecretWriteResult rotated = service.rotate(provider.id(), "secret-v2");
        assertTrue(rotated.rotated());
        assertEquals(2, rotated.version());

        byte[] stored = port.get(IdpSecretService.secretName(provider));
        assertArrayEquals("secret-v2".getBytes(StandardCharsets.UTF_8), stored);
        Arrays.fill(stored, (byte) 0);

        IdpSecretService.ConnectionTestResult tested = service.testConnection(provider.id());
        assertEquals("SUCCEEDED", tested.status());
        assertTrue(Arrays.stream(toUnsigned(testedBytes.get())).allMatch(value -> value == 0),
                "plaintext byte buffer must be cleared after connection testing");
        Model.IdpConfiguration persisted = repository.findIdpConfiguration(TENANT, provider.id()).orElseThrow();
        assertEquals(Boolean.TRUE, persisted.lastSyncOk());
        assertEquals(0, persisted.syncFailCount());

        String audit = audits.chain().stream().map(item -> item.event().eventType() + item.event().details())
                .reduce("", (left, right) -> left + right);
        assertTrue(audit.contains("p2.idp.secret.written"));
        assertTrue(audit.contains("p2.idp.secret.rotated"));
        assertTrue(audit.contains("p2.idp.connection.tested"));
        assertFalse(audit.contains("secret-v1"));
        assertFalse(audit.contains("secret-v2"));
        assertFalse(first.toString().contains("secret-v1"));
        assertFalse(rotated.toString().contains("secret-v2"));
        assertFalse(tested.toString().contains("secret-v2"));
    }

    @Test
    void failedConnectionTestStoresOnlySafeFailureMetadata() {
        Clock clock = Clock.systemUTC();
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(repository, idp, new KeycloakSmtpHook(idp, false),
                clock, Duration.ofHours(1));
        TenantAdminInitializer.Result admin = module.adminInitializer().initialize(
                new TenantAdminInitializer.Command(TENANT, "IdP Admin", "idp-failure-admin@example.test", "idp-failure-admin"));
        bind(admin);
        Model.IdpConfiguration provider = new IdpConfigurationService(repository, clock, AdminMfaPolicy.forced())
                .create(new IdpConfigurationService.Command("OIDC", "Corp SSO", "client",
                        "https://id.example.test/realms/corp", "DISABLED"));
        var crypto = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        var port = new LocalEncryptedSecretAdapter(crypto);
        port.put(IdpSecretService.secretName(provider), "bad-secret".getBytes(StandardCharsets.UTF_8));
        var audits = new InMemoryHashChainedAuditPort(crypto);
        IdpSecretService service = new IdpSecretService(repository, port, audits, clock,
                AdminMfaPolicy.forced(), (configuration, secret) -> false);

        assertEquals("FAILED", service.testConnection(provider.id()).status());
        Model.IdpConfiguration saved = repository.findIdpConfiguration(TENANT, provider.id()).orElseThrow();
        assertEquals(Boolean.FALSE, saved.lastSyncOk());
        assertEquals(1, saved.syncFailCount());
        assertFalse(audits.chain().getFirst().event().details().toString().contains("bad-secret"));
    }

    private static void bind(TenantAdminInitializer.Result admin) {
        Instant now = Instant.now();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), admin.tenantId(), admin.accountId(),
                admin.personId(), admin.keycloakUserId(), now, now.plusSeconds(3600)));
    }

    @Test
    void connectionTesterRefusesHttpAndPrivateNetworkIssuersBeforeSendingCredentials() {
        var tester = new one.nexus.p2.idp.OidcClientCredentialsTester(new tools.jackson.databind.ObjectMapper());
        Instant now = Instant.now();
        Model.IdpConfiguration http = new Model.IdpConfiguration(UUID.randomUUID(), TENANT, "OIDC", "Test",
                "client", "http://127.0.0.1:8080/realm", "DISABLED", null, null, 0,
                null, now, now, 0);
        Model.IdpConfiguration privateHttps = new Model.IdpConfiguration(UUID.randomUUID(), TENANT, "OIDC", "Test",
                "client", "https://127.0.0.1/realm", "DISABLED", null, null, 0,
                null, now, now, 0);
        byte[] secret = "must-not-be-sent".getBytes(StandardCharsets.UTF_8);

        assertFalse(tester.test(http, secret));
        assertFalse(tester.test(privateHttps, secret));
    }

    private static int[] toUnsigned(byte[] bytes) {
        return bytes == null ? new int[0] : java.util.stream.IntStream.range(0, bytes.length)
                .map(index -> bytes[index] & 0xff).toArray();
    }
}
