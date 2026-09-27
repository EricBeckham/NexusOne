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
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMappingPut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** DPO-L3: a save that loses the conditional write is a 409, records who saved, and history is clamped. */
class PrintMappingConcurrencyTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final PrintMappingStore store = mock(PrintMappingStore.class);
    private final PrintMappingService service = new PrintMappingService(isolation, market, store);
    private final UUID id = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service.loadContract();
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(actor, null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                UUID.randomUUID(), null, null, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
        when(store.source()).thenReturn(ProductPrintMapping.SOURCE_STORED);
        when(store.note()).thenReturn(ProductPrintMapping.NOTE_STORED);
        when(store.find(id)).thenReturn(Optional.empty());
    }

    @Test
    void losingTheConditionalWriteIsAConflict() {
        when(store.saveIfRevision(any(), eq(0L), eq(actor), any())).thenReturn(false);
        assertThatThrownBy(() -> service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT",
                List.of(new FieldSlotMapping("title", "form.fields")), 0L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void acceptedSaveIsStoredWithTheActorAndReportsTheStoreSource() {
        when(store.saveIfRevision(any(), eq(0L), eq(actor), any())).thenReturn(true);
        ProductPrintMapping saved = service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT",
                List.of(new FieldSlotMapping("title", "form.fields")), 0L));
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.source()).isEqualTo("STORED");
        assertThat(saved.note()).contains("重启后仍在");
    }

    @Test
    void historyIsClampedAndGuarded() {
        service.history(id, 500);
        verify(store).history(id, 100);
        service.history(id, 0);
        verify(store).history(id, 1);
        verify(isolation, times(2)).requirePlatformContext("print-mapping.history", PlatformDuty.SYSTEM_ADMIN);
    }
}
