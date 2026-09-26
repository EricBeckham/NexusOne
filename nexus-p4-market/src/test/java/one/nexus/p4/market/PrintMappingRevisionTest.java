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
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMappingPut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** CF-RD3: honest process-local revision and no silent overwrite when the editor names the revision it saw. */
class PrintMappingRevisionTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final PrintMappingService service = new PrintMappingService(isolation, market, new InMemoryPrintMappingStore());
    private final UUID id = UUID.randomUUID();
    private final List<FieldSlotMapping> mappings = List.of(new FieldSlotMapping("title", "form.fields"));

    @BeforeEach
    void setUp() throws Exception {
        service.loadContract();
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                UUID.randomUUID(), null, null, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    @Test
    void defaultIsRevisionZeroAndEachAcceptedPutAddsOne() {
        assertThat(service.get(id).revision()).isZero();
        assertThat(service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", mappings, 0L)).revision()).isEqualTo(1);
        assertThat(service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", mappings, 1L)).revision()).isEqualTo(2);
        assertThat(service.get(id).revision()).isEqualTo(2);
    }

    @Test
    void staleExpectedRevisionIsRefusedAndNothingChanges() {
        service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", mappings, 0L));
        assertThatThrownBy(() -> service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", List.of(), 0L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("expected revision 0, current 1");
        assertThat(service.get(id).mappings()).isEqualTo(mappings);
        assertThat(service.get(id).revision()).isEqualTo(1);
    }

    @Test
    void omittingExpectedRevisionKeepsTheCfR3Behaviour() {
        service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", mappings));
        assertThat(service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT", List.of())).revision()).isEqualTo(2);
    }
}
