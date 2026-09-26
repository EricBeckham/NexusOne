package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.LinkedHashMap;
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
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.ProductConfigImportModels.ImportAccepted;
import one.nexus.p4.market.ProductConfigImportModels.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductConfigImportServiceTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final ClockPort clock = () -> Instant.parse("2026-09-26T16:00:00Z");
    private final ProductConfigImportJobStore jobs = new InMemoryProductConfigImportJobStore();
    private final ProductConfigImportService service = new ProductConfigImportService(
            market, isolation, audit, clock, jobs, new P1Properties());

    private void allow() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    private Map<String, Object> samplePackage(String code) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "nexusone.product-config");
        manifest.put("formatVersion", "1.0");
        manifest.put("checksum", "sha256:abc");
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("code", code);
        product.put("name", "请假");
        product.put("description", "d");
        product.put("formTemplateCode", "demo.leave");
        product.put("formTemplateVersion", 1);
        product.put("processDefinitionKey", null);
        product.put("processDefinitionVersion", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("manifest", manifest);
        body.put("formTemplates", List.of());
        body.put("processDefinitions", List.of());
        body.put("productDefinition", product);
        body.put("skuBindings", List.of());
        return body;
    }

    @Test
    void createsDraftWhenNoConflict() {
        allow();
        UUID formId = UUID.randomUUID();
        when(market.publishedFormTemplateVersionByCode("demo.leave", 1)).thenReturn(Optional.of(formId));
        when(market.productDefinitionByCode("demo.leave-approval")).thenReturn(Optional.empty());
        ImportAccepted accepted = service.importPackage(samplePackage("demo.leave-approval"), "skip", "key-1");
        assertThat(accepted.status()).isEqualTo(JobStatus.COMPLETED);
        verify(market).saveProductDefinition(argThat(d -> d.code().equals("demo.leave-approval") && "DRAFT".equals(d.status())));
        assertThat(service.importPackage(samplePackage("demo.leave-approval"), "skip", "key-1").importJobId())
                .isEqualTo(accepted.importJobId());
    }

    @Test
    void failStrategyRefusesPublishedOverwrite() {
        allow();
        UUID formId = UUID.randomUUID();
        when(market.publishedFormTemplateVersionByCode("demo.leave", 1)).thenReturn(Optional.of(formId));
        when(market.productDefinitionByCode("demo.leave-approval")).thenReturn(Optional.of(new ProductDefinition(
                UUID.randomUUID(), "demo.leave-approval", "请假", "d", formId, null, null, Map.of(),
                "PUBLISHED", Instant.EPOCH, Instant.EPOCH, 1)));
        assertThatThrownBy(() -> service.importPackage(samplePackage("demo.leave-approval"), "fail", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("refuse overwrite");
        verify(market, never()).saveProductDefinition(any());
    }
}