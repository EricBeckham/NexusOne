package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.FormFieldSnapshot;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductConfigExportServiceTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final ClockPort clock = () -> Instant.parse("2026-09-26T15:00:00Z");
    @SuppressWarnings("unchecked")
    private final ObjectProvider<one.nexus.p3.flow.ProcessDefinitionPublishService> processes = mock(ObjectProvider.class);
    private final ProductConfigExportService service = new ProductConfigExportService(
            market, isolation, audit, clock, processes, new P1Properties());

    @Test
    void exportsFormat10BundleWithChecksumAndSkus() {
        UUID definitionId = UUID.randomUUID();
        UUID formVersionId = UUID.randomUUID();
        when(isolation.requirePlatformContext(eq("product-definition.export"), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(definitionId)).thenReturn(Optional.of(new ProductDefinition(
                definitionId, "demo.leave-approval", "请假审批产品", "desc", formVersionId,
                "demo.simple-approval", 1, Map.of(), "PUBLISHED", Instant.EPOCH, Instant.EPOCH, 1)));
        when(market.formTemplateVersionSnapshot(formVersionId)).thenReturn(Optional.of(new FormTemplateVersionSnapshot(
                formVersionId, "demo.leave", "请假申请", 1,
                List.of(new FormFieldSnapshot("reason", "事由", "TEXTAREA", true)))));
        when(market.publishedFormTemplateVersion(formVersionId)).thenReturn(Optional.of(formVersionId));
        when(market.skusBoundToProductDefinition(definitionId)).thenReturn(List.of(
                new Sku("demo.simple-approval", UUID.randomUUID(), "标准请假开通", Map.of("approval", new MarketModels.EntitlementSpec(true, null, Map.of())))));
        when(processes.getIfAvailable()).thenReturn(null);

        Map<String, Object> bundle = service.exportBundle(definitionId);
        assertThat(bundle.get("printSupport")).isEqualTo("config+preview-later");
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) bundle.get("manifest");
        assertThat(manifest.get("format")).isEqualTo("nexusone.product-config");
        assertThat(manifest.get("formatVersion")).isEqualTo("1.0");
        assertThat(String.valueOf(manifest.get("checksum"))).startsWith("sha256:");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forms = (List<Map<String, Object>>) bundle.get("formTemplates");
        assertThat(forms.get(0).get("code")).isEqualTo("demo.leave");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skus = (List<Map<String, Object>>) bundle.get("skuBindings");
        assertThat(skus).hasSize(1);
        assertThat(service.suggestedFilename(bundle)).isEqualTo("demo.leave-approval.product-config.json");
        verify(audit).append(argThat(event -> event.eventType().equals("platform.product_definition.exported")));
    }
}