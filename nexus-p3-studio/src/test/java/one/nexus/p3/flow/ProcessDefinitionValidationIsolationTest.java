package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.persist.JdbcProcessDefinitionDraftStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessDefinitionValidationIsolationTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PERSON = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private final JdbcProcessDefinitionDraftStore store = Mockito.mock(JdbcProcessDefinitionDraftStore.class);
    private final ProcessDefinitionDraftService drafts = new ProcessDefinitionDraftService(store);
    private final ProcessDefinitionValidationService service = new ProcessDefinitionValidationService(
            drafts, new ProcessDefinitionValidator());

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void validationCannotReadAnotherTenantsDraft() {
        bind(TENANT_A);
        when(store.find(TENANT_A, "privateFlow")).thenReturn(Optional.of(new ProcessDefinitionDraft(
                UUID.randomUUID(), TENANT_A, "privateFlow", "Private", "<definitions/>", 1, PERSON,
                Instant.now(), Instant.now())));
        service.validateDraft("privateFlow");

        bind(TENANT_B);
        when(store.find(TENANT_B, "privateFlow")).thenReturn(Optional.empty());
        assertThrows(FlowException.class, () -> service.validateDraft("privateFlow"));
        verify(store).find(TENANT_B, "privateFlow");
    }

    private static void bind(UUID tenant) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, UUID.randomUUID(), PERSON, "m8-test", Instant.now(), Instant.now().plusSeconds(60)));
    }
}
