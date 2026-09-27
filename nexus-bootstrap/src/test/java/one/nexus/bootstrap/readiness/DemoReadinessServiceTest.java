package one.nexus.bootstrap.readiness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import one.nexus.bootstrap.readiness.DemoReadinessService.DemoReadiness;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

/** DL-L3: presence only — booleans, fixed words, variable names; platform guard first. */
class DemoReadinessServiceTest {
    private static final String SECRET = "dl-l3-not-a-real-secret-91b2";
    private final IsolationGuard isolation = mock(IsolationGuard.class);

    @SuppressWarnings("unchecked")
    private DemoReadinessService service(MockEnvironment env) {
        ObjectProvider<HealthEndpoint> health = mock(ObjectProvider.class);
        return new DemoReadinessService(env, isolation, health);
    }

    private static MockEnvironment readyProd() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("nexus.iam.idp", "keycloak")
                .withProperty("nexus.crypto.key-source", "env")
                .withProperty("nexus.iam.persistence", "jdbc")
                .withProperty("nexus.permission.persistence", "jdbc")
                .withProperty("nexus.iam.keycloak.issuer", "https://id.example.invalid/realms/nexus")
                .withProperty("nexus.iam.keycloak.client-id", "nexus-platform")
                .withProperty("nexus.iam.keycloak.client-secret", SECRET)
                .withProperty("nexus.iam.keycloak.redirect-uri", "https://app.example.invalid/api/v1/auth/callback");
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void aReadyProdHostReportsPresenceOnly() {
        DemoReadiness readiness = service(readyProd()).snapshot();
        assertTrue(readiness.prodProfileActive());
        assertFalse(readiness.demoProfileActive());
        assertEquals("keycloak", readiness.idpMode());
        assertTrue(readiness.keycloakIssuerConfigured());
        assertTrue(readiness.keycloakIssuerHttps());
        assertTrue(readiness.keycloakClientConfigured());
        assertEquals(List.of(), readiness.missingKeycloakVariables());
        assertTrue(readiness.demoFlagsOff());
        assertTrue(readiness.cryptoKeyFromEnv());
        assertTrue(readiness.durablePersistence());
        assertEquals(0, readiness.prodGuardViolationCount());
        assertEquals("UNKNOWN", readiness.health(), "no health endpoint means UNKNOWN, never UP");
        assertTrue(readiness.note().contains("预备 ≠ 验收"));
        assertFalse(readiness.toString().contains(SECRET));
        assertFalse(readiness.toString().contains("id.example.invalid"));
    }

    @Test
    void aLocalStubHostNamesWhatIsMissingWithoutValues() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("nexus.iam.idp", "stub")
                .withProperty("nexus.demo.enabled", "true")
                .withProperty("nexus.iam.keycloak.issuer", "http://127.0.0.1:8080/realms/nexus")
                .withProperty("nexus.iam.keycloak.client-id", "nexus-platform")
                .withProperty("nexus.iam.keycloak.client-secret", "")
                .withProperty("nexus.iam.keycloak.redirect-uri", "https://REPLACE_DEMO_HOST/cb");
        env.setActiveProfiles("demo");
        DemoReadiness readiness = service(env).snapshot();
        assertFalse(readiness.prodProfileActive());
        assertTrue(readiness.demoProfileActive());
        assertEquals("stub", readiness.idpMode());
        assertFalse(readiness.keycloakIssuerConfigured(), "the local default issuer does not count");
        assertFalse(readiness.keycloakIssuerHttps());
        assertFalse(readiness.keycloakClientConfigured());
        assertEquals(List.of("NEXUS_IAM_KEYCLOAK_ISSUER", "NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "NEXUS_IAM_KEYCLOAK_REDIRECT_URI"),
                readiness.missingKeycloakVariables());
        assertFalse(readiness.demoFlagsOff());
        assertFalse(readiness.toString().contains("127.0.0.1"));
        assertFalse(readiness.toString().contains("REPLACE_DEMO_HOST"));
    }

    @Test
    void anUnexpectedIdpValueIsReportedAsOtherNotEchoed() {
        MockEnvironment env = readyProd().withProperty("nexus.iam.idp", "custom-" + SECRET);
        assertEquals("other", service(env).snapshot().idpMode());
    }

    @Test
    void readRequiresThePlatformGuardFirst() {
        when(isolation.requirePlatformContext("demo-readiness.read", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> service(readyProd()).read());
    }
}
