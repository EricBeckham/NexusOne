package one.nexus.bootstrap.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionProfileGuardTest {

    private static MockEnvironment hardenedProd() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("nexus.crypto.key-source", "env")
                .withProperty("nexus.iam.idp", "keycloak")
                .withProperty("nexus.iam.persistence", "jdbc")
                .withProperty("nexus.permission.persistence", "jdbc")
                .withProperty("nexus.files.storage", "s3")
                .withProperty("management.endpoint.health.show-details", "when-authorized")
                .withProperty("nexus.iam.keycloak.issuer", "https://id.example.invalid/realms/nexus")
                .withProperty("nexus.iam.keycloak.client-id", "nexus-platform")
                .withProperty("nexus.iam.keycloak.client-secret", SECRET)
                .withProperty("nexus.iam.keycloak.redirect-uri", "https://app.example.invalid/api/v1/auth/callback");
        env.setActiveProfiles("prod");
        return env;
    }

    /** Test-only stand-in; the assertions prove it never appears in a message. */
    private static final String SECRET = "dpo-l1-not-a-real-secret-7f3a";

    @Test void hardenedProdHasNoViolationsOrWarnings() {
        MockEnvironment env = hardenedProd();
        assertEquals(List.of(), ProductionProfileGuard.violations(env));
        assertEquals(List.of(), ProductionProfileGuard.warnings(env));
    }

    @Test void prodDefaultsFromTheBaseConfigAreAllRefused() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("nexus.crypto.key-source", "ephemeral")
                .withProperty("management.endpoint.health.show-details", "always");
        env.setActiveProfiles("prod");
        List<String> violations = ProductionProfileGuard.violations(env);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("nexus.crypto.key-source")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("nexus.iam.idp")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("nexus.iam.persistence")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("nexus.permission.persistence")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("management.endpoint.health")), violations::toString);
    }

    @Test void demoProfileAndSwitchesAreRefusedUnderProd() {
        MockEnvironment env = hardenedProd()
                .withProperty("nexus.demo.enabled", "true")
                .withProperty("nexus.p1.demo-context.enabled", "TRUE")
                .withProperty("nexus.studio.demo-context", "true");
        env.setActiveProfiles("prod", "demo");
        List<String> violations = ProductionProfileGuard.violations(env);
        assertEquals(4, violations.size(), violations::toString);
    }

    @Test void prodRefusesAnonymousActuatorEndpointsBeyondHealth() {
        MockEnvironment env = hardenedProd()
                .withProperty("management.endpoints.web.exposure.include", "health, info,metrics");
        List<String> violations = ProductionProfileGuard.violations(env);
        assertEquals(2, violations.size(), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("'metrics'")), violations::toString);
        env.setProperty("management.endpoints.web.exposure.include", "*");
        assertEquals(1, ProductionProfileGuard.violations(env).size());
    }

    @Test void configurationRefusesToCreateTheCheckBeanOnViolation() {
        MockEnvironment env = hardenedProd().withProperty("nexus.iam.idp", "stub");
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new ProductionProfileConfiguration().productionProfileCheck(env));
        assertTrue(refused.getMessage().contains("nexus.iam.idp"), refused.getMessage());
    }

    @Test void prodWarnsAboutMemoryFilesBootstrapTokensAndPlainHttpIssuer() {
        MockEnvironment env = hardenedProd()
                .withProperty("nexus.files.storage", "memory")
                .withProperty("nexus.iam.platform-bootstrap-token", "set")
                .withProperty("nexus.iam.keycloak.issuer", "http://kc.internal/realms/nexus");
        assertEquals(3, ProductionProfileGuard.warnings(env).size(), () -> ProductionProfileGuard.warnings(env).toString());
    }

    @Test void keycloakProdNamesEveryMissingVariableAndNeverAValue() {
        MockEnvironment env = hardenedProd()
                .withProperty("nexus.iam.keycloak.client-secret", "")
                .withProperty("nexus.iam.keycloak.issuer", "http://127.0.0.1:8080/realms/nexus")
                .withProperty("nexus.iam.keycloak.client-id", " ")
                .withProperty("nexus.iam.keycloak.redirect-uri", "https://REPLACE_DEMO_HOST/REPLACE_CALLBACK_PATH");
        List<String> violations = ProductionProfileGuard.violations(env);
        assertEquals(4, violations.size(), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET is not set")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("NEXUS_IAM_KEYCLOAK_ISSUER is not set")
                && v.contains("local development default")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("NEXUS_IAM_KEYCLOAK_CLIENT_ID is not set")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("NEXUS_IAM_KEYCLOAK_REDIRECT_URI still holds a template placeholder")), violations::toString);
        String joined = String.join("\n", violations);
        assertFalse(joined.contains("127.0.0.1"), joined);
        assertFalse(joined.contains("REPLACE_DEMO_HOST"), joined);
    }

    @Test void refusalMessageCarriesNamesButNoSecretValue() {
        MockEnvironment env = hardenedProd()
                .withProperty("nexus.iam.keycloak.issuer", "")
                .withProperty("nexus.iam.keycloak.client-secret", "REPLACE_FROM_SECRET_STORE-" + SECRET);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new ProductionProfileConfiguration().productionProfileCheck(env));
        assertTrue(refused.getMessage().contains("NEXUS_IAM_KEYCLOAK_ISSUER"), refused.getMessage());
        assertTrue(refused.getMessage().contains("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET"), refused.getMessage());
        assertFalse(refused.getMessage().contains(SECRET), refused.getMessage());
        assertFalse(refused.getMessage().contains("id.example.invalid"), refused.getMessage());
    }

    @Test void theDocumentedEnvironmentVariablesSatisfyTheGuard() {
        org.springframework.core.env.StandardEnvironment env = new org.springframework.core.env.StandardEnvironment();
        env.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource("test-env", java.util.Map.of(
                "NEXUS_IAM_KEYCLOAK_ISSUER", "https://id.example.invalid/realms/nexus",
                "NEXUS_IAM_KEYCLOAK_CLIENT_ID", "nexus-platform",
                "NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "https://app.example.invalid/api/v1/auth/callback")));
        List<String> violations = ProductionProfileGuard.keycloakViolations(env);
        assertEquals(List.of("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET is not set (nexus.iam.keycloak.client-secret); "
                + "required when prod runs with nexus.iam.idp=keycloak"), violations);
    }

    @Test void completeKeycloakSettingsStartAndOtherIdpsAreNotChecked() {
        assertEquals(List.of(), ProductionProfileGuard.violations(hardenedProd()));
        MockEnvironment ldapLike = hardenedProd()
                .withProperty("nexus.iam.idp", "oidc-other")
                .withProperty("nexus.iam.keycloak.client-secret", "");
        assertEquals(List.of(), ProductionProfileGuard.violations(ldapLike));
        MockEnvironment dev = new MockEnvironment().withProperty("nexus.iam.idp", "keycloak");
        assertEquals(List.of(), ProductionProfileGuard.violations(dev));
    }

    @Test void outsideProdOnlyTheEphemeralKeyIsReportedAndNothingIsRefused() {
        MockEnvironment env = new MockEnvironment().withProperty("nexus.iam.idp", "stub");
        assertEquals(List.of(), ProductionProfileGuard.violations(env));
        assertEquals(1, ProductionProfileGuard.warnings(env).size());
        env.setProperty("nexus.crypto.key-source", "env");
        assertEquals(List.of(), ProductionProfileGuard.warnings(env));
    }
}
