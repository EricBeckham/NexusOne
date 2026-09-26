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
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p3.flow.ProcessDefinitionPublishService;
import one.nexus.p4.market.MarketModels.AppVersion;
import one.nexus.p4.market.MarketModels.ConfigReadinessStep;
import one.nexus.p4.market.MarketModels.ProductConfigReadiness;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ReadinessStatus;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.VersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** PC6: wizard readiness is computed from stored data and never reports a step done that is not. */
class ProductConfigReadinessTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final ProcessDefinitionPublishService publish = mock(ProcessDefinitionPublishService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProcessDefinitionPublishService> processes = mock(ObjectProvider.class);
    private final ClockPort clock = () -> Instant.parse("2026-09-26T00:00:00Z");
    private final ProductDefinitionService service = new ProductDefinitionService(market, isolation,
            mock(AuditPort.class), clock, processes, new one.nexus.p1.config.P1Properties());
    private final UUID id = UUID.randomUUID();
    private final UUID formVersion = UUID.randomUUID();
    private final UUID appId = UUID.randomUUID();

    private void platform() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    private void definition(String processKey, Integer processVersion) {
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "hr", "HR", null, formVersion,
                processKey, processVersion, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    private static Map<String, ReadinessStatus> statuses(ProductConfigReadiness readiness) {
        return readiness.steps().stream().collect(java.util.stream.Collectors.toMap(ConfigReadinessStep::key, ConfigReadinessStep::status));
    }

    @Test
    void nothingBoundIsNotReadyEverywhere() {
        platform();
        definition(null, null);
        when(market.publishedFormTemplateVersion(formVersion)).thenReturn(Optional.empty());
        ProductConfigReadiness readiness = service.readiness(id);
        assertThat(readiness.complete()).isFalse();
        assertThat(readiness.definitionStatus()).isEqualTo("DRAFT");
        assertThat(readiness.steps()).extracting(ConfigReadinessStep::key).containsExactly("form", "process", "sku", "publish");
        assertThat(readiness.steps()).extracting(ConfigReadinessStep::status).containsOnly(ReadinessStatus.NOT_READY);
        verify(market, never()).versions(any());
    }

    @Test
    void everyStepDoneOnlyWhenStoredDataProvesIt() {
        platform();
        definition("leave", 2);
        when(market.publishedFormTemplateVersion(formVersion)).thenReturn(Optional.of(formVersion));
        when(processes.getIfAvailable()).thenReturn(publish);
        when(publish.isPublishedVersionAvailable("leave", 2)).thenReturn(true);
        when(market.skusBoundToProductDefinition(id)).thenReturn(List.of(new Sku("HR_BASIC", appId, "HR", Map.of())));
        when(market.versions(appId)).thenReturn(List.of(new AppVersion(UUID.randomUUID(), appId, "1.0.0",
                VersionStatus.PUBLISHED, null, Instant.EPOCH, Instant.EPOCH)));
        ProductConfigReadiness readiness = service.readiness(id);
        assertThat(statuses(readiness)).containsOnlyKeys("form", "process", "sku", "publish").allSatisfy((key, status) -> assertThat(status).isEqualTo(ReadinessStatus.DONE));
        assertThat(readiness.complete()).isTrue();
        assertThat(readiness.steps().get(2).detail()).contains("HR_BASIC");
    }

    @Test
    void boundSkuWithoutPublishedAppVersionIsNotPublished() {
        platform();
        definition("leave", 2);
        when(market.publishedFormTemplateVersion(formVersion)).thenReturn(Optional.of(formVersion));
        when(processes.getIfAvailable()).thenReturn(publish);
        when(publish.isPublishedVersionAvailable("leave", 2)).thenReturn(false);
        when(market.skusBoundToProductDefinition(id)).thenReturn(List.of(new Sku("HR_BASIC", appId, "HR", Map.of())));
        when(market.versions(appId)).thenReturn(List.of(new AppVersion(UUID.randomUUID(), appId, "1.0.0",
                VersionStatus.APPROVED, null, Instant.EPOCH, null)));
        Map<String, ReadinessStatus> statuses = statuses(service.readiness(id));
        assertThat(statuses).containsEntry("form", ReadinessStatus.DONE).containsEntry("process", ReadinessStatus.NOT_READY)
                .containsEntry("sku", ReadinessStatus.DONE).containsEntry("publish", ReadinessStatus.NOT_READY);
    }

    @Test
    void processCheckThatCannotRunIsUnknownNotDone() {
        platform();
        definition("leave", 2);
        when(market.publishedFormTemplateVersion(formVersion)).thenReturn(Optional.of(formVersion));
        when(processes.getIfAvailable()).thenReturn(null);
        assertThat(statuses(service.readiness(id))).containsEntry("process", ReadinessStatus.UNKNOWN);
        when(processes.getIfAvailable()).thenReturn(publish);
        when(publish.isPublishedVersionAvailable("leave", 2)).thenThrow(new IllegalStateException("engine down"));
        ProductConfigReadiness readiness = service.readiness(id);
        assertThat(statuses(readiness)).containsEntry("process", ReadinessStatus.UNKNOWN);
        assertThat(readiness.complete()).isFalse();
    }

    @Test
    void missingDefinitionIs404AndRequiresPlatformAdmin() {
        platform();
        when(market.productDefinition(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.readiness(id)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(isolation).requirePlatformContext("product-definition.readiness", PlatformDuty.SYSTEM_ADMIN);
    }
}