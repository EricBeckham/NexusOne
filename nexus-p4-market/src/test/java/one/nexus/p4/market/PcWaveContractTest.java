package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import one.nexus.p4.market.MarketModels.ConfigReadinessStep;
import one.nexus.p4.market.MarketModels.ProductConfigReadiness;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ReadinessStatus;
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.PrintField;
import one.nexus.p4.market.ProductSampleModels.PrintSample;
import one.nexus.p4.market.ProductSampleModels.PrintSection;
import one.nexus.p4.market.ProductSampleModels.ReportMetric;
import one.nexus.p4.market.ProductSampleModels.ReportSample;
import one.nexus.p4.market.ProductSampleModels.SampleColumn;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** PC14: the wizard readiness and the three product samples keep their shape and their honesty rules. */
class PcWaveContractTest {
    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static ConfigReadinessStep step(String key, ReadinessStatus status) {
        return new ConfigReadinessStep(key, key, status, "");
    }

    @Test
    void readinessShapeAndCompleteOnlyWhenEveryStepIsDone() {
        assertThat(components(ProductConfigReadiness.class)).containsExactly("productDefinitionId", "definitionStatus", "complete", "steps");
        assertThat(components(ConfigReadinessStep.class)).containsExactly("key", "label", "status", "detail");
        assertThat(ReadinessStatus.values()).extracting(Enum::name).containsExactly("DONE", "NOT_READY", "UNKNOWN");
        ProductDefinition definition = new ProductDefinition(UUID.randomUUID(), "c", "n", null, UUID.randomUUID(), null, null,
                Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1);
        assertThat(ProductConfigReadiness.of(definition, List.of()).complete()).isFalse();
        assertThat(ProductConfigReadiness.of(definition, List.of(step("form", ReadinessStatus.DONE),
                step("process", ReadinessStatus.UNKNOWN))).complete()).isFalse();
        assertThat(ProductConfigReadiness.of(definition, List.of(step("form", ReadinessStatus.DONE),
                step("process", ReadinessStatus.DONE))).complete()).isTrue();
    }

    @Test
    void sampleShapesAreStable() {
        assertThat(components(LedgerSample.class)).containsExactly("productDefinitionId", "productName", "source",
                "formTemplateVersionId", "formTemplateName", "formTemplateVersion", "columns", "rows", "note");
        assertThat(components(SampleColumn.class)).containsExactly("key", "label", "type", "required");
        assertThat(components(PrintSample.class)).containsExactly("productDefinitionId", "productName", "source",
                "templateCode", "templateName", "paper", "orientation", "title", "sections", "note");
        assertThat(components(PrintSection.class)).containsExactly("key", "label", "fields");
        assertThat(components(PrintField.class)).containsExactly("key", "label", "type");
        assertThat(components(ReportSample.class)).containsExactly("productDefinitionId", "productName", "asOf",
                "metrics", "trendAvailable", "trendNote", "note");
        assertThat(components(ReportMetric.class)).containsExactly("key", "label", "value", "note");
        assertThat(ReportMetric.class.getRecordComponents()[2].getType()).isEqualTo(Long.class);
    }

    @Test
    void noSampleCarriesValuesMoneyOrATimeSeries() {
        for (Class<?> record : List.of(LedgerSample.class, SampleColumn.class, PrintSample.class, PrintSection.class,
                PrintField.class, ReportSample.class, ReportMetric.class, ProductConfigReadiness.class, ConfigReadinessStep.class)) {
            for (String name : components(record)) {
                String lower = name.toLowerCase(Locale.ROOT);
                for (String banned : List.of("price", "amount", "fee", "invoice", "coupon", "billing", "payment",
                        "series", "points", "history", "growth", "percent")) {
                    assertThat(lower).as(record.getSimpleName() + "." + name).doesNotContain(banned);
                }
            }
        }
        assertThat(components(PrintField.class)).doesNotContain("value");
        assertThat(new LedgerSample(null, null, LedgerSample.SOURCE, null, null, null, List.of(), List.of(), "").rows()).isEmpty();
    }

    @Test
    void pathsAreTheOnesTheWebClientCalls() throws NoSuchMethodException {
        assertThat(ProductDefinitionController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/p4/product-definitions");
        assertThat(ProductDefinitionController.class.getMethod("readiness", UUID.class).getAnnotation(GetMapping.class).value())
                .containsExactly("/{id}/config-readiness");
        assertThat(ProductSampleController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/p4/product-definitions/{id}/samples");
        for (String kind : List.of("ledger", "print", "report")) {
            assertThat(ProductSampleController.class.getMethod(kind, UUID.class).getAnnotation(GetMapping.class).value())
                    .containsExactly("/" + kind);
        }
    }
}