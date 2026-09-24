package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
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
import one.nexus.p4.market.MarketModels.ProductDefinitionWrite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

class ProductDefinitionServiceTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final ClockPort clock = () -> Instant.parse("2026-09-25T00:00:00Z");
    @SuppressWarnings("unchecked")
    private final ObjectProvider<one.nexus.p3.flow.ProcessDefinitionPublishService> processes = mock(ObjectProvider.class);
    private final ProductDefinitionService service = new ProductDefinitionService(market, isolation, audit, clock, processes);
    private final UUID versionId = UUID.randomUUID();

    private void allowPlatform() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.publishedFormTemplateVersion(versionId)).thenReturn(Optional.of(versionId));
    }

    private ProductDefinitionWrite request() {
        return new ProductDefinitionWrite("hr-basic", "HR 基础版", "desc", versionId, null, null, Map.of());
    }

    @Test void createsBoundDraftAndAuditsIt() {
        allowPlatform();
        var created = service.create(request());
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.formTemplateVersionId()).isEqualTo(versionId);
        verify(market).saveProductDefinition(created);
        verify(audit).append(any());
    }

    @Test void rejectsUnpublishedFormVersion() {
        allowPlatform();
        when(market.publishedFormTemplateVersion(versionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ResponseStatusException.class);
        verify(market, never()).saveProductDefinition(any());
    }

    @Test void requiresSystemAdminPlatformContext() {
        when(isolation.requirePlatformContext("product-definition.create", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(market);
    }

    @Test void bindsSkuAndWritesAudit() {
        allowPlatform();
        UUID definitionId = UUID.randomUUID();
        var definition = new one.nexus.p4.market.MarketModels.ProductDefinition(definitionId, "hr-basic", "HR 基础版",
                "desc", versionId, null, null, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1);
        when(market.sku("HR_BASIC")).thenReturn(Optional.of(new one.nexus.p4.market.MarketModels.Sku("HR_BASIC", UUID.randomUUID(), "HR Basic", Map.of())));
        when(market.productDefinition(definitionId)).thenReturn(Optional.of(definition));
        assertThat(service.bindSku("HR_BASIC", definitionId)).isEqualTo(definition);
        verify(market).bindSkuToProductDefinition("HR_BASIC", definitionId);
        verify(audit).append(argThat(event -> event.eventType().equals("platform.product_definition.sku_bound")));
    }
}
