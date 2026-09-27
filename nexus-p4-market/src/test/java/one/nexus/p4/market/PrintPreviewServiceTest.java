package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p3.port.ProductApplicationStore;
import one.nexus.p3.port.ProductApplicationStore.ApplicationSnapshot;
import one.nexus.p4.market.MarketModels.FormFieldSnapshot;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintPreviewModels.PreviewField;
import one.nexus.p4.market.PrintPreviewModels.PreviewSection;
import one.nexus.p4.market.PrintPreviewModels.PrintPreview;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** DPO-L5: no application = empty values with a note; application values only for the caller's own tenant. */
class PrintPreviewServiceTest {
    private static final UUID ALPHA = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID BETA = UUID.fromString("01900000-0000-7000-8000-0000000000b2");
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final ProductApplicationStore applications = mock(ProductApplicationStore.class);
    private final InMemoryPrintMappingStore mappings = new InMemoryPrintMappingStore();
    private final IsolationKernel kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
    private final UUID product = UUID.randomUUID();
    private final UUID formVersion = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private PrintPreviewService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        PrintMappingService printMapping = new PrintMappingService(isolation, market, mappings);
        printMapping.loadContract();
        ObjectProvider<ProductApplicationStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(applications);
        service = new PrintPreviewService(isolation, market, printMapping, mappings, provider);
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new one.nexus.p1.domain.Actor(UUID.randomUUID(), null, "t", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.productDefinition(product)).thenReturn(Optional.of(new ProductDefinition(product, "leave", "请假报销", null,
                formVersion, "leave", 1, Map.of(), "DRAFT", Instant.EPOCH, Instant.EPOCH, 1)));
        when(market.formTemplateVersionSnapshot(formVersion)).thenReturn(Optional.of(new FormTemplateVersionSnapshot(formVersion,
                "leave_form", "请假单", 1, List.of(new FormFieldSnapshot("title", "事由", "TEXT", true),
                        new FormFieldSnapshot("days", "天数", "NUMBER", false),
                        new FormFieldSnapshot("note", "备注", "TEXTAREA", false)))));
        when(applications.findForTenant(ALPHA, applicationId)).thenReturn(Optional.of(new ApplicationSnapshot(applicationId,
                null, product, formVersion, "{\"title\":\"年假\",\"days\":3}", "RUNNING", UUID.randomUUID(),
                Instant.parse("2026-09-27T08:00:00Z"))));
        when(applications.findForTenant(BETA, applicationId)).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    private <T> T as(UUID tenant, Callable<T> call) {
        var context = kernel.tenants().issue(new AuthenticatedMembership(Actor.person("dpo-l5"), tenant.toString(),
                tenant.toString(), Set.of(tenant.toString())), "dpo-l5-trace");
        return AccessContextHolder.call(context, call);
    }

    private static PreviewField field(PrintPreview preview, String key) {
        return preview.sections().stream().flatMap(s -> s.fields().stream()).filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void productPreviewHasNoValuesAndSaysSo() {
        PrintPreview preview = service.forProduct(product);
        assertThat(preview.dataSource()).isEqualTo("NONE");
        assertThat(preview.applicationId()).isNull();
        assertThat(preview.note()).contains("未绑定真实单据");
        assertThat(preview.sections()).extracting(PreviewSection::key).containsExactly("header", "form", "approval");
        assertThat(preview.sections().stream().flatMap(s -> s.fields().stream())).allSatisfy(f -> {
            assertThat(f.value()).isNull();
            assertThat(f.valueNote()).isNull();
        });
        assertThat(preview.mappingSource()).isEqualTo("DEFAULT");
        verify(isolation).requirePlatformContext("print-preview.product", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void applicationPreviewFillsValuesThroughTheSavedMapping() throws Exception {
        mappings.saveIfRevision(new ProductPrintMapping(product, "IN_MEMORY", "A5", "LANDSCAPE", "builtin.approval-form.a4.v1",
                List.of(new FieldSlotMapping("title", "header.title")), "", 1), 0, null, Instant.now());
        PrintPreview preview = as(ALPHA, () -> service.forApplication(applicationId));
        assertThat(preview.dataSource()).isEqualTo("APPLICATION");
        assertThat(preview.applicationId()).isEqualTo(applicationId);
        assertThat(preview.paper()).isEqualTo("A5");
        assertThat(preview.mappingRevision()).isEqualTo(1);
        PreviewField title = field(preview, "title");
        assertThat(title.slotId()).isEqualTo("header.title");
        assertThat(title.value()).isEqualTo("年假");
        assertThat(preview.sections().get(0).fields()).extracting(PreviewField::key).contains("title");
        assertThat(field(preview, "days").value()).isEqualTo("3");
        assertThat(field(preview, "days").slotId()).isEqualTo("form.fields");
        assertThat(field(preview, "application_no").value()).isEqualTo(applicationId.toString());
        assertThat(field(preview, "submitted_at").value()).isEqualTo("2026-09-27T08:00:00Z");
    }

    @Test
    void unresolvedValuesAreNullWithAReasonNeverInvented() throws Exception {
        PrintPreview preview = as(ALPHA, () -> service.forApplication(applicationId));
        assertThat(field(preview, "note").value()).isNull();
        assertThat(field(preview, "note").valueNote()).contains("没有填写");
        assertThat(field(preview, "applicant").value()).isNull();
        assertThat(field(preview, "applicant").valueNote()).contains("暂不解析");
        assertThat(field(preview, "opinion").value()).isNull();
        assertThat(field(preview, "opinion").valueNote()).contains("不在本预览");
    }

    @Test
    void anotherTenantsApplicationIsNotFoundAndPlatformOrAnonymousIsRefused() {
        assertThatThrownBy(() -> as(BETA, () -> service.forApplication(applicationId)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.forApplication(applicationId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(applications, never()).findForTenant(isNull(), any());
    }
}
