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
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;
import one.nexus.p4.market.ProductConfigImportModels.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** IM6: import job read API + success/conflict/bad-package contracts. */
class ProductConfigImportContractTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final ClockPort clock = () -> Instant.parse("2026-09-26T16:30:00Z");
    private final ProductConfigImportJobStore jobs = new InMemoryProductConfigImportJobStore();
    private ProductConfigImportService service;

    @BeforeEach
    void setUp() {
        service = new ProductConfigImportService(market, isolation, audit, clock, jobs, new P1Properties());
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    private Map<String, Object> pkg(String code) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "nexusone.product-config");
        manifest.put("formatVersion", "1.0");
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("code", code);
        product.put("name", "请假");
        product.put("formTemplateCode", "demo.leave");
        product.put("formTemplateVersion", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("manifest", manifest);
        body.put("formTemplates", List.of());
        body.put("processDefinitions", List.of());
        body.put("productDefinition", product);
        body.put("skuBindings", List.of());
        return body;
    }

    @Test
    void successJobReadableWithCounts() {
        UUID formId = UUID.randomUUID();
        when(market.publishedFormTemplateVersionByCode("demo.leave", 1)).thenReturn(Optional.of(formId));
        when(market.productDefinitionByCode("ok-1")).thenReturn(Optional.empty());
        ImportAccepted accepted = service.importPackage(pkg("ok-1"), "skip", null);
        ImportJob job = service.getJob(accepted.importJobId());
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.counts().get("created")).isEqualTo(1L);
        assertThat(job.results()).isNotEmpty();
    }

    @Test
    void conflictFailPersistsFailedJob() {
        UUID formId = UUID.randomUUID();
        when(market.publishedFormTemplateVersionByCode("demo.leave", 1)).thenReturn(Optional.of(formId));
        when(market.productDefinitionByCode("pub-1")).thenReturn(Optional.of(new ProductDefinition(
                UUID.randomUUID(), "pub-1", "请假", "d", formId, null, null, Map.of(),
                "PUBLISHED", Instant.EPOCH, Instant.EPOCH, 1)));
        assertThatThrownBy(() -> service.importPackage(pkg("pub-1"), "fail", "idem-fail"))
                .isInstanceOf(ResponseStatusException.class);
        ImportJob job = jobs.findByIdempotencyKey("idem-fail").orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.error()).contains("refuse overwrite");
    }

    @Test
    void badPackageRejected() {
        Map<String, Object> bad = Map.of("manifest", Map.of("format", "nope", "formatVersion", "9.9"));
        assertThatThrownBy(() -> service.importPackage(bad, "skip", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingJobIsNotFound() {
        assertThatThrownBy(() -> service.getJob(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}