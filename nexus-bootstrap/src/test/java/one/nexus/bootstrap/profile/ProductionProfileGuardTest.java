package one.nexus.bootstrap.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .withProperty("management.endpoint.health.show-details", "when-authorized");
        env.setActiveProfiles("prod");
        return env;
    }

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

    @Test void outsideProdOnlyTheEphemeralKeyIsReportedAndNothingIsRefused() {
        MockEnvironment env = new MockEnvironment().withProperty("nexus.iam.idp", "stub");
        assertEquals(List.of(), ProductionProfileGuard.violations(env));
        assertEquals(1, ProductionProfileGuard.warnings(env).size());
        env.setProperty("nexus.crypto.key-source", "env");
        assertEquals(List.of(), ProductionProfileGuard.warnings(env));
    }
}
