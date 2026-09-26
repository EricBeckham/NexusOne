package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.UpgradeFacts;
import one.nexus.p4.market.MarketModels.UpgradeSteps;
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
 * XB13: upgrade steps/facts through the product mapper (SNAKE_CASE, non_null). Pins what the web stepper
 * reads — including that an undeclared fact's value is ABSENT on the wire (not null), so the client must
 * treat a missing value as 暂无.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class MarketUpgradeWireNamingTest {
    private static EmbeddedPostgres postgres;

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
    }

    @Test
    void stepsAndFactsUseSnakeCaseAndOmitUndeclaredValues() {
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        JsonNode steps = mapper.valueToTree(UpgradeSteps.unrecorded(id));
        assertEquals(id.toString(), steps.path("installation_id").asText());
        assertTrue(steps.path("installationId").isMissingNode());
        assertEquals(false, steps.path("recorded").asBoolean(true));
        assertEquals(6, steps.path("steps").size());
        for (JsonNode step : steps.path("steps")) {
            assertEquals("UNKNOWN", step.path("status").asText());
            assertTrue(!step.path("key").asText().isBlank() && !step.path("label").asText().isBlank());
        }

        JsonNode facts = mapper.valueToTree(UpgradeFacts.current(
                new Installation(id, UUID.randomUUID(), UUID.randomUUID(), version, InstallationStatus.INSTALLED, Instant.now(), Instant.now())));
        assertEquals(version.toString(), facts.path("current_version_id").asText());
        JsonNode compatibility = facts.path("facts").get(0);
        assertEquals("compatibility", compatibility.path("key").asText());
        assertTrue(compatibility.path("value").isMissingNode(), "undeclared value is omitted, never sent as a fake value");
        assertTrue(!compatibility.path("note").asText().isBlank());
        assertEquals("仅切换版本", facts.path("facts").get(3).path("value").asText());
    }
}