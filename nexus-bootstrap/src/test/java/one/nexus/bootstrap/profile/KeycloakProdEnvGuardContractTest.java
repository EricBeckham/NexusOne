package one.nexus.bootstrap.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import one.nexus.bootstrap.profile.ProductionProfileGuard.Presence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * DL-L5: the Keycloak / prod environment guard (DPO-L1) as a contract. For every variable and every way it can be
 * wrong, the refusal names exactly that variable and nothing else; no message or log line ever carries a value.
 */
class KeycloakProdEnvGuardContractTest {
    /** Test-only stand-ins, distinctive so any leak is visible. */
    private static final String SECRET = "dl-l5-fake-secret-e3c1a9";
    private static final String CLIENT = "dl-l5-fake-client-77";
    private static final String HOST = "dl-l5-idp.example.invalid";

    private static final Map<String, String> PROPERTY = Map.of(
            "NEXUS_IAM_KEYCLOAK_ISSUER", "nexus.iam.keycloak.issuer",
            "NEXUS_IAM_KEYCLOAK_CLIENT_ID", "nexus.iam.keycloak.client-id",
            "NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "nexus.iam.keycloak.client-secret",
            "NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "nexus.iam.keycloak.redirect-uri");

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(ProductionProfileConfiguration.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    static MockEnvironment readyProd() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("nexus.crypto.key-source", "env")
                .withProperty("nexus.iam.idp", "keycloak")
                .withProperty("nexus.iam.persistence", "jdbc")
                .withProperty("nexus.permission.persistence", "jdbc")
                .withProperty("nexus.files.storage", "s3")
                .withProperty("management.endpoint.health.show-details", "when-authorized")
                .withProperty("nexus.iam.keycloak.issuer", "https://" + HOST + "/realms/nexus")
                .withProperty("nexus.iam.keycloak.client-id", CLIENT)
                .withProperty("nexus.iam.keycloak.client-secret", SECRET)
                .withProperty("nexus.iam.keycloak.redirect-uri", "https://" + HOST + "/api/v1/auth/callback");
        env.setActiveProfiles("prod");
        return env;
    }

    /** variable × (blank, placeholder, local default where one exists). */
    static Stream<Arguments> brokenSettings() {
        return Stream.of(
                Arguments.of("NEXUS_IAM_KEYCLOAK_ISSUER", "", Presence.MISSING),
                Arguments.of("NEXUS_IAM_KEYCLOAK_ISSUER", "https://REPLACE_KEYCLOAK_HOST/realms/REPLACE_REALM", Presence.PLACEHOLDER),
                Arguments.of("NEXUS_IAM_KEYCLOAK_ISSUER", "http://127.0.0.1:8080/realms/nexus", Presence.DEV_DEFAULT),
                Arguments.of("NEXUS_IAM_KEYCLOAK_CLIENT_ID", "   ", Presence.MISSING),
                Arguments.of("NEXUS_IAM_KEYCLOAK_CLIENT_ID", "REPLACE_CONFIDENTIAL_CLIENT_ID", Presence.PLACEHOLDER),
                Arguments.of("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "", Presence.MISSING),
                Arguments.of("NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "REPLACE_FROM_SECRET_STORE", Presence.PLACEHOLDER),
                Arguments.of("NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "", Presence.MISSING),
                Arguments.of("NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "https://REPLACE_DEMO_HOST/REPLACE_CALLBACK_PATH", Presence.PLACEHOLDER),
                Arguments.of("NEXUS_IAM_KEYCLOAK_REDIRECT_URI", "http://127.0.0.1:8080/api/v1/auth/callback", Presence.DEV_DEFAULT));
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("brokenSettings")
    void eachBrokenVariableIsRefusedByNameOnly(String variable, String value, Presence expected) {
        MockEnvironment env = readyProd().withProperty(PROPERTY.get(variable), value);
        assertEquals(expected, ProductionProfileGuard.keycloakPresence(env).get(variable));

        List<String> violations = ProductionProfileGuard.violations(env);
        assertEquals(1, violations.size(), violations::toString);
        String message = violations.get(0);
        assertTrue(message.startsWith(variable + " "), message);
        assertTrue(message.contains("(" + PROPERTY.get(variable)), message);
        if (!value.isBlank()) assertFalse(message.contains(value.trim()), "value echoed: " + message);
        for (String other : PROPERTY.keySet()) {
            if (!other.equals(variable)) assertFalse(message.contains(other), message);
        }

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new ProductionProfileConfiguration().productionProfileCheck(env));
        assertNoValues(refused.getMessage());
        assertTrue(refused.getMessage().contains(variable), refused.getMessage());
    }

    @Test
    void allFourMissingAreAllNamedInTemplateOrder() {
        MockEnvironment env = readyProd();
        PROPERTY.values().forEach(property -> env.setProperty(property, ""));
        assertEquals(List.of("NEXUS_IAM_KEYCLOAK_ISSUER", "NEXUS_IAM_KEYCLOAK_CLIENT_ID",
                        "NEXUS_IAM_KEYCLOAK_CLIENT_SECRET", "NEXUS_IAM_KEYCLOAK_REDIRECT_URI"),
                ProductionProfileGuard.violations(env).stream().map(v -> v.substring(0, v.indexOf(' '))).toList());
    }

    @Test
    void warningsAreLoggedWithoutValues() {
        MockEnvironment env = readyProd()
                .withProperty("nexus.iam.keycloak.issuer", "http://" + HOST + "/realms/nexus")
                .withProperty("nexus.iam.platform-bootstrap-token", "dl-l5-bootstrap-token-" + SECRET)
                .withProperty("nexus.files.storage", "memory");
        new ProductionProfileConfiguration().productionProfileCheck(env);
        List<String> lines = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(3, lines.size(), lines::toString);
        assertTrue(lines.stream().anyMatch(line -> line.contains("nexus.iam.keycloak.issuer is not https")), lines::toString);
        lines.forEach(KeycloakProdEnvGuardContractTest::assertNoValues);
        assertFalse(String.join("\n", lines).contains("dl-l5-bootstrap-token"), lines::toString);
    }

    @Test
    void aCompleteKeycloakProdStartsAndLogsNothingSensitive() {
        new ProductionProfileConfiguration().productionProfileCheck(readyProd());
        logs.list.forEach(event -> assertNoValues(event.getFormattedMessage()));
    }

    static void assertNoValues(String text) {
        assertFalse(text.contains(SECRET), text);
        assertFalse(text.contains(CLIENT), text);
        assertFalse(text.contains(HOST), text);
        assertFalse(text.contains("127.0.0.1"), text);
        assertFalse(text.matches("(?s).*REPLACE_[A-Z_]+.*"), text);
    }
}
