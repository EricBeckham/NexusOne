package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import one.nexus.p4.market.PrintMappingModels.PrintMappingContract;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMappingPut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** CF-R3: static print-mapping contract + in-memory product override (zero Flyway). */
class PrintMappingServiceTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final InMemoryPrintMappingStore store = new InMemoryPrintMappingStore();
    private final PrintMappingService service =
            new PrintMappingService(isolation, market, store, new ObjectMapper());
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service.loadContract();
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test",
                        new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace",
                        null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                UUID.randomUUID(), "leave", 1, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    @Test
    void contractExposesPaperEnumAndBuiltinSlots() {
        PrintMappingContract c = service.contract();
        assertThat(c.contractVersion()).isEqualTo("1.0");
        assertThat(c.source()).isEqualTo("STATIC");
        assertThat(c.paperSizes()).extracting(p -> p.code()).contains("A4", "A5", "LETTER");
        assertThat(c.orientations()).extracting(o -> o.code()).containsExactly("PORTRAIT", "LANDSCAPE");
        assertThat(c.builtinTemplate().code()).isEqualTo("builtin.approval-form.a4.v1");
        assertThat(c.builtinTemplate().defaultPaper()).isEqualTo("A4");
        assertThat(c.builtinTemplate().slots()).extracting(s -> s.id())
                .contains("header.title", "form.fields", "approval.opinion");
        assertThat(c.defaultFieldMappings()).isNotEmpty();
        assertThat(c.mappingRules().unmappedSpillSlotId()).isEqualTo("form.fields");
        verify(isolation).requirePlatformContext("print-mapping.contract", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void getReturnsDefaultMappingUntilPutOverridesInMemory() {
        ProductPrintMapping def = service.get(id);
        assertThat(def.source()).isEqualTo(ProductPrintMapping.SOURCE_DEFAULT);
        assertThat(def.paper()).isEqualTo("A4");
        assertThat(def.orientation()).isEqualTo("PORTRAIT");
        assertThat(def.mappings()).isEqualTo(service.loadedContract().defaultFieldMappings());

        ProductPrintMappingPut put = new ProductPrintMappingPut("A5", "LANDSCAPE",
                List.of(new FieldSlotMapping("title", "header.title")));
        ProductPrintMapping saved = service.put(id, put);
        assertThat(saved.source()).isEqualTo(ProductPrintMapping.SOURCE_IN_MEMORY);
        assertThat(saved.paper()).isEqualTo("A5");
        assertThat(saved.orientation()).isEqualTo("LANDSCAPE");
        assertThat(saved.mappings()).containsExactly(new FieldSlotMapping("title", "header.title"));

        assertThat(service.get(id).source()).isEqualTo(ProductPrintMapping.SOURCE_IN_MEMORY);
        verify(isolation).requirePlatformContext("print-mapping.write", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void putRejectsUnknownPaperOrSlotOrDuplicateField() {
        assertThatThrownBy(() -> service.put(id, new ProductPrintMappingPut("A3", "PORTRAIT", List.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unknown paper");
        assertThatThrownBy(() -> service.put(id,
                new ProductPrintMappingPut("A4", "PORTRAIT", List.of(new FieldSlotMapping("x", "no.such")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unknown slotId");
        assertThatThrownBy(() -> service.put(id, new ProductPrintMappingPut("A4", "PORTRAIT",
                List.of(new FieldSlotMapping("a", "header.title"), new FieldSlotMapping("a", "form.fields")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("duplicate fieldKey");
    }

    @Test
    void get404WhenProductMissing() {
        UUID missing = UUID.randomUUID();
        when(market.productDefinition(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(ResponseStatusException.class);
    }
}