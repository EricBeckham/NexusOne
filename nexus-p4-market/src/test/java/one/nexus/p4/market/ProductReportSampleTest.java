package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.FormFieldSnapshot;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ProductUsageCounts;
import one.nexus.p4.market.ProductSampleModels.ReportMetric;
import one.nexus.p4.market.ProductSampleModels.ReportSample;
import org.junit.jupiter.api.Test;

/** PC13: honest current counts; unknown stays null; never a trend. */
class ProductReportSampleTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final Instant now = Instant.parse("2026-09-26T12:00:00Z");
    private final ProductSampleService service = new ProductSampleService(market, isolation, () -> now);
    private final UUID id = UUID.randomUUID();
    private final UUID formVersion = UUID.randomUUID();

    private void product() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                formVersion, null, null, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    private static Map<String, Long> values(ReportSample sample) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        sample.metrics().forEach(metric -> out.put(metric.key(), metric.value()));
        return out;
    }

    @Test
    void countsComeFromStoredRowsAndThereIsNoTrend() {
        product();
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.of(new FormTemplateVersionSnapshot(
                formVersion, "f", "表单", 1, List.of(new FormFieldSnapshot("a", "A", "TEXT", false), new FormFieldSnapshot("b", "B", "TEXT", false)))));
        when(market.productUsageCounts(id)).thenReturn(new ProductUsageCounts(1, 3, 2));
        ReportSample sample = service.report(id);
        assertThat(sample.metrics()).extracting(ReportMetric::key)
                .containsExactly("form_fields", "bound_skus", "active_subscriptions", "installed_tenants");
        assertThat(values(sample)).containsEntry("form_fields", 2L).containsEntry("bound_skus", 1L)
                .containsEntry("active_subscriptions", 3L).containsEntry("installed_tenants", 2L);
        assertThat(sample.trendAvailable()).isFalse();
        assertThat(sample.trendNote()).contains("趋势暂无");
        assertThat(sample.asOf()).isEqualTo(now);
        verify(isolation).requirePlatformContext("product-sample.report", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void zeroIsZeroButAFailedCountIsNullNotZero() {
        product();
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.empty());
        when(market.productUsageCounts(id)).thenThrow(new IllegalStateException("db"));
        ReportSample sample = service.report(id);
        assertThat(values(sample)).containsEntry("form_fields", null).containsEntry("bound_skus", null)
                .containsEntry("active_subscriptions", null).containsEntry("installed_tenants", null);
        assertThat(sample.metrics()).allSatisfy(metric -> assertThat(metric.note()).isNotBlank());
        assertThat(sample.trendAvailable()).isFalse();

        doReturn(new ProductUsageCounts(0, 0, 0)).when(market).productUsageCounts(id);
        assertThat(values(service.report(id))).containsEntry("bound_skus", 0L).containsEntry("installed_tenants", 0L);
    }
}