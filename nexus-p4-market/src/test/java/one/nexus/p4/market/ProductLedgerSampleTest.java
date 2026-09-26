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
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.SampleColumn;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** PC9: the sample ledger is the product's form fields as columns and never carries invented rows. */
class ProductLedgerSampleTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final ProductSampleService service = new ProductSampleService(market, isolation);
    private final UUID id = UUID.randomUUID();
    private final UUID formVersion = UUID.randomUUID();

    private void product() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(UUID.randomUUID(), null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(id)).thenReturn(Optional.of(new ProductDefinition(id, "leave", "请假报销", null,
                formVersion, null, null, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
    }

    @Test
    void columnsFollowTheBoundFormVersionAndRowsStayEmpty() {
        product();
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.of(new FormTemplateVersionSnapshot(
                formVersion, "leave_form", "请假单", 3, List.of(
                        new FormFieldSnapshot("title", "事由", "TEXT", true),
                        new FormFieldSnapshot("days", "天数", "NUMBER", false)))));
        LedgerSample sample = service.ledger(id);
        assertThat(sample.productName()).isEqualTo("请假报销");
        assertThat(sample.source()).isEqualTo("FORM_TEMPLATE_VERSION");
        assertThat(sample.formTemplateName()).isEqualTo("请假单");
        assertThat(sample.formTemplateVersion()).isEqualTo(3);
        assertThat(sample.columns()).containsExactly(new SampleColumn("title", "事由", "TEXT", true),
                new SampleColumn("days", "天数", "NUMBER", false));
        assertThat(sample.rows()).isEmpty();
        assertThat(sample.note()).contains("没有记录");
        verify(isolation).requirePlatformContext("product-sample.ledger", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void unreadableFormVersionGivesNoColumnsAndSaysSo() {
        product();
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.empty());
        LedgerSample sample = service.ledger(id);
        assertThat(sample.columns()).isEmpty();
        assertThat(sample.rows()).isEmpty();
        assertThat(sample.formTemplateVersionId()).isEqualTo(formVersion);
        assertThat(sample.formTemplateName()).isNull();
        assertThat(sample.note()).isEqualTo(LedgerSample.MISSING_NOTE);
    }

    @Test
    void unknownProductIs404() {
        product();
        UUID other = UUID.randomUUID();
        when(market.productDefinition(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ledger(other)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}