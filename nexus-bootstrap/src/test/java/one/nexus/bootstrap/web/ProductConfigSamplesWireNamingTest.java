package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p4.market.MarketModels.ConfigReadinessStep;
import one.nexus.p4.market.MarketModels.ProductConfigReadiness;
import one.nexus.p4.market.MarketModels.ReadinessStatus;
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.PrintField;
import one.nexus.p4.market.ProductSampleModels.PrintSample;
import one.nexus.p4.market.ProductSampleModels.PrintSection;
import one.nexus.p4.market.ProductSampleModels.ReportMetric;
import one.nexus.p4.market.ProductSampleModels.ReportSample;
import one.nexus.p4.market.ProductSampleModels.SampleColumn;
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
 * PC14: the PC6 readiness and PC9/PC11/PC13 samples through the product mapper (SNAKE_CASE, non_null) —
 * the exact keys the wizard and ProductSampleView read. An uncountable metric has no "value" key at all.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class ProductConfigSamplesWireNamingTest {
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
    void readinessKeysMatchTheWizard() {
        JsonNode node = mapper.valueToTree(new ProductConfigReadiness(UUID.randomUUID(), "DRAFT", false,
                List.of(new ConfigReadinessStep("sku", "绑 SKU", ReadinessStatus.NOT_READY, "还没有绑定 SKU。"))));
        assertTrue(node.path("product_definition_id").isTextual());
        assertEquals("DRAFT", node.path("definition_status").asText());
        assertFalse(node.path("complete").asBoolean(true));
        assertEquals("sku", node.path("steps").path(0).path("key").asText());
        assertEquals("NOT_READY", node.path("steps").path(0).path("status").asText());
        assertEquals("还没有绑定 SKU。", node.path("steps").path(0).path("detail").asText());
    }

    @Test
    void sampleKeysMatchProductSampleView() {
        JsonNode ledger = mapper.valueToTree(new LedgerSample(UUID.randomUUID(), "产品", LedgerSample.SOURCE, UUID.randomUUID(),
                "表单", 2, List.of(new SampleColumn("title", "事由", "TEXT", true)), List.of(), LedgerSample.NOTE));
        assertEquals("表单", ledger.path("form_template_name").asText());
        assertEquals(2, ledger.path("form_template_version").asInt());
        assertTrue(ledger.path("columns").path(0).path("required").asBoolean());
        assertTrue(ledger.path("rows").isArray() && ledger.path("rows").isEmpty());

        JsonNode print = mapper.valueToTree(new PrintSample(UUID.randomUUID(), "产品", PrintSample.SOURCE, PrintSample.TEMPLATE_CODE,
                PrintSample.TEMPLATE_NAME, "A4", "PORTRAIT", "产品 申请单",
                List.of(new PrintSection("form", "申请内容", List.of(new PrintField("title", "事由", "TEXT")))), PrintSample.NOTE));
        assertEquals("builtin.approval-form.a4.v1", print.path("template_code").asText());
        assertEquals("PORTRAIT", print.path("orientation").asText());
        assertEquals("事由", print.path("sections").path(0).path("fields").path(0).path("label").asText());
        assertTrue(print.path("sections").path(0).path("fields").path(0).path("value").isMissingNode());

        JsonNode report = mapper.valueToTree(new ReportSample(UUID.randomUUID(), "产品", Instant.parse("2026-09-26T00:00:00Z"),
                List.of(new ReportMetric("bound_skus", "绑定 SKU", 0L, "n"), new ReportMetric("installed_tenants", "已开通租户", null, "暂时无法统计。")),
                false, ReportSample.TREND_NOTE, ReportSample.NOTE));
        assertTrue(report.path("as_of").isTextual());
        assertFalse(report.path("trend_available").asBoolean(true));
        assertEquals(ReportSample.TREND_NOTE, report.path("trend_note").asText());
        assertEquals(0, report.path("metrics").path(0).path("value").asInt(-1));
        assertTrue(report.path("metrics").path(1).path("value").isMissingNode(), "uncountable is absent, never 0");
        assertTrue(report.path("series").isMissingNode());
    }
}