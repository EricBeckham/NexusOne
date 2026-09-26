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
import one.nexus.p4.market.ProductSampleModels.PrintField;
import one.nexus.p4.market.ProductSampleModels.PrintSample;
import one.nexus.p4.market.ProductSampleModels.PrintSection;
import org.junit.jupiter.api.Test;

/** PC11: one built-in print layout, filled with the product's own form fields and nothing invented. */
class ProductPrintSampleTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final ProductSampleService service = new ProductSampleService(market, isolation, () -> Instant.EPOCH);
    private final UUID id = UUID.randomUUID();
    private final UUID formVersion = UUID.randomUUID();

    private void product(String processKey, Integer processVersion) {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                formVersion, processKey, processVersion, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    @Test
    void builtInLayoutCarriesTheProductFormAndApprovalBlockWhenAProcessIsBound() {
        product("leave", 1);
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.of(new FormTemplateVersionSnapshot(
                formVersion, "leave_form", "请假单", 1, List.of(new FormFieldSnapshot("title", "事由", "TEXT", true)))));
        PrintSample sample = service.print(id);
        assertThat(sample.source()).isEqualTo("BUILTIN");
        assertThat(sample.templateCode()).isEqualTo(PrintSample.TEMPLATE_CODE);
        assertThat(sample.paper()).isEqualTo("A4");
        assertThat(sample.orientation()).isEqualTo("PORTRAIT");
        assertThat(sample.title()).isEqualTo("请假报销 申请单");
        assertThat(sample.sections()).extracting(PrintSection::key).containsExactly("header", "form", "approval");
        assertThat(sample.sections().get(1).fields()).containsExactly(new PrintField("title", "事由", "TEXT"));
        assertThat(sample.note()).contains("不是打印设计器");
        verify(isolation).requirePlatformContext("product-sample.print", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void noProcessMeansNoApprovalBlockAndUnreadableFormMeansAnEmptyFormSection() {
        product(null, null);
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.empty());
        PrintSample sample = service.print(id);
        assertThat(sample.sections()).extracting(PrintSection::key).containsExactly("header", "form");
        assertThat(sample.sections().get(1).fields()).isEmpty();
    }
}