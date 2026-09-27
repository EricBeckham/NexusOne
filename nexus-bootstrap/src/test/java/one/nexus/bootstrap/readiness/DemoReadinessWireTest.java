package one.nexus.bootstrap.readiness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.regex.Pattern;
import one.nexus.bootstrap.NexusOneApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DL-L3: in the real application (actuator health present) the readiness snapshot serialises through the
 * product mapper as snake_case presence, with every configured Keycloak value — including the secret — absent.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class DemoReadinessWireTest {
    private static final String SECRET = "dl-l3-wire-secret-5c8e";
    private static final String ISSUER_HOST = "idp-dl-l3.example.invalid";
    private static EmbeddedPostgres postgres;

    @Autowired DemoReadinessService readiness;
    @Autowired ObjectMapper mapper;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.iam.keycloak.issuer", () -> "https://" + ISSUER_HOST + "/realms/nexus");
        registry.add("nexus.iam.keycloak.client-id", () -> "dl-l3-client-id");
        registry.add("nexus.iam.keycloak.client-secret", () -> SECRET);
        registry.add("nexus.iam.keycloak.redirect-uri", () -> "https://" + ISSUER_HOST + "/api/v1/auth/callback");
    }

    @Test
    void presenceOnlyOnTheWire() throws Exception {
        JsonNode wire = mapper.valueToTree(readiness.snapshot());
        String text = mapper.writeValueAsString(wire);
        assertFalse(text.contains(SECRET), text);
        assertFalse(text.contains(ISSUER_HOST), text);
        assertFalse(text.contains("dl-l3-client-id"), "client id value is not returned either");
        assertTrue(wire.path("keycloak_client_configured").asBoolean(false), text);
        assertTrue(wire.path("keycloak_issuer_configured").asBoolean(false));
        assertTrue(wire.path("keycloak_issuer_https").asBoolean(false));
        assertTrue(wire.path("keycloak_client_configured").asBoolean(false));
        assertEquals(0, wire.path("missing_keycloak_variables").size());
        assertTrue(List.of("UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN").contains(wire.path("health").asText()), text);
        assertTrue(wire.path("idp_mode").isTextual());
        // Every field is a boolean, a number, a fixed word, the note, or a list of variable names.
        Pattern envName = Pattern.compile("NEXUS_[A-Z_]+");
        wire.path("missing_keycloak_variables").forEach(name -> assertTrue(envName.matcher(name.asText()).matches()));
        wire.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            assertTrue(value.isBoolean() || value.isNumber() || value.isArray() || value.isTextual(), entry.getKey());
            if (value.isTextual() && !entry.getKey().equals("note")) {
                assertTrue(value.asText().matches("[a-z]+|[A-Z_]+"), entry.getKey() + "=" + value.asText());
            }
        });
    }
}
