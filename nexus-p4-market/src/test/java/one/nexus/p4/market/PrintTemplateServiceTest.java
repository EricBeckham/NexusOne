package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplateCopy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** DPO-L4: built-in first and read-only; copies are new metadata rows that never touch the built-in. */
class PrintTemplateServiceTest {
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final List<PrintTemplate> rows = new ArrayList<>();
    private final PrintTemplateStore store = new PrintTemplateStore() {
        @Override public List<PrintTemplate> copies() { return List.copyOf(rows); }
        @Override public void insert(PrintTemplate template) { rows.add(template); }
    };
    private final UUID actor = UUID.randomUUID();
    private PrintTemplateService service;

    @BeforeEach
    void setUp() throws Exception {
        PrintMappingService mapping = new PrintMappingService(isolation, mock(MarketRepository.class), new InMemoryPrintMappingStore());
        mapping.loadContract();
        service = new PrintTemplateService(isolation, mapping, store, () -> Instant.parse("2026-09-27T00:00:00Z"));
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "test", new Actor(actor, null, "test", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    @Test
    void listStartsWithTheReadOnlyBuiltin() {
        List<PrintTemplate> list = service.list();
        assertThat(list).hasSize(1);
        PrintTemplate builtin = list.get(0);
        assertThat(builtin.code()).isEqualTo("builtin.approval-form.a4.v1");
        assertThat(builtin.kind()).isEqualTo("BUILTIN");
        assertThat(builtin.editable()).isFalse();
        assertThat(builtin.id()).isNull();
        verify(isolation).requirePlatformContext("print-template.list", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void copyCreatesANewEditableRowAndLeavesTheBuiltinAlone() {
        PrintTemplate copy = service.copy(new PrintTemplateCopy(null, "请假单 A5", "A5", "LANDSCAPE"));
        assertThat(copy.id()).isNotNull();
        assertThat(copy.code()).startsWith("copy.").isNotEqualTo("builtin.approval-form.a4.v1");
        assertThat(copy.kind()).isEqualTo("COPY");
        assertThat(copy.sourceTemplateCode()).isEqualTo("builtin.approval-form.a4.v1");
        assertThat(copy.paper()).isEqualTo("A5");
        assertThat(copy.orientation()).isEqualTo("LANDSCAPE");
        assertThat(copy.createdBy()).isEqualTo(actor);
        assertThat(copy.note()).contains("later");

        PrintTemplate second = service.copy(null);
        assertThat(second.id()).isNotEqualTo(copy.id());
        assertThat(second.code()).isNotEqualTo(copy.code());
        assertThat(second.name()).isEqualTo("副本 · 内置样例 · 审批单（A4 纵向）");
        assertThat(second.paper()).isEqualTo("A4");

        List<PrintTemplate> list = service.list();
        assertThat(list).extracting(PrintTemplate::kind).containsExactly("BUILTIN", "COPY", "COPY");
        assertThat(list.get(0).paper()).isEqualTo("A4");
        assertThat(list.get(0).editable()).isFalse();
    }

    @Test
    void onlyTheBuiltinCanBeCopiedAndInputsFollowTheContract() {
        assertThatThrownBy(() -> service.copy(new PrintTemplateCopy("copy.abc", null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.copy(new PrintTemplateCopy(null, null, "A3", null))).hasMessageContaining("unknown paper");
        assertThatThrownBy(() -> service.copy(new PrintTemplateCopy(null, null, null, "DIAGONAL"))).hasMessageContaining("unknown orientation");
        assertThatThrownBy(() -> service.copy(new PrintTemplateCopy(null, "x".repeat(161), null, null))).hasMessageContaining("too long");
        assertThat(rows).isEmpty();
    }
}
