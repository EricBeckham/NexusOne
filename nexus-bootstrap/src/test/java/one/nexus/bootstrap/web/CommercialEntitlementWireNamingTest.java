package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p4.market.CommercialEntitlementService.CommercialEntitlement;
import one.nexus.p4.market.CommercialEntitlementService.EntitlementLine;
import one.nexus.p4.market.CommercialEntitlementService.EntitlementSummary;
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
 * XB14: commercial entitlement views through the product mapper (SNAKE_CASE, non_null). Pins the summary
 * keys the subscription table reads, and that unknown / not-applicable values are ABSENT on the wire:
 * a missing `projected` means unknown (no tag), missing version/time means not projected.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class CommercialEntitlementWireNamingTest {
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

    private static CommercialEntitlement view(UUID subscription, EntitlementSummary summary) {
        return new CommercialEntitlement(subscription, UUID.randomUUID(), "TEAM", "团队版", "ACTIVE", UUID.randomUUID(),
                null, null, List.of(new EntitlementLine("apps", true, 10)), summary);
    }

    @Test
    void summaryKeysAreSnakeCaseAndUnknownsAreAbsent() {
        Instant projectedAt = Instant.parse("2026-09-26T00:00:00Z");
        UUID subscription = UUID.randomUUID();
        JsonNode projected = mapper.valueToTree(view(subscription, new EntitlementSummary(2, 3, true, 4L, projectedAt)));
        assertEquals(subscription.toString(), projected.path("subscription_id").asText());
        JsonNode summary = projected.path("summary");
        assertEquals(2, summary.path("enabled_count").asInt());
        assertEquals(3, summary.path("total_count").asInt());
        assertTrue(summary.path("projected").asBoolean(false));
        assertEquals(4, summary.path("projection_version").asInt());
        assertTrue(summary.path("projected_at").isTextual());
        assertTrue(summary.path("enabledCount").isMissingNode());

        JsonNode notProjected = mapper.valueToTree(view(UUID.randomUUID(), new EntitlementSummary(1, 1, false, null, null))).path("summary");
        assertEquals(false, notProjected.path("projected").asBoolean(true));
        assertTrue(notProjected.path("projection_version").isMissingNode());
        assertTrue(notProjected.path("projected_at").isMissingNode());

        JsonNode unknown = mapper.valueToTree(view(UUID.randomUUID(), new EntitlementSummary(1, 1, null, null, null))).path("summary");
        assertTrue(unknown.path("projected").isMissingNode(), "unknown is omitted, never sent as false");

        JsonNode noInstall = mapper.valueToTree(view(UUID.randomUUID(), new EntitlementSummary(0, 0, false, null, null)));
        assertTrue(noInstall.path("installation_id").isMissingNode());
    }
}