package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.persist.JdbcProcessDefinitionDraftStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessDefinitionDraftServiceTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PERSON = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private final JdbcProcessDefinitionDraftStore store = Mockito.mock(JdbcProcessDefinitionDraftStore.class);
    private final ProcessDefinitionDraftService service = new ProcessDefinitionDraftService(store);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void listsAndSavesOnlyForTheServerIssuedTenant() {
        bind(TENANT_A);
        when(store.list(TENANT_A)).thenReturn(List.of());
        service.list();
        service.save("expense", "Expense", "<definitions/>");
        verify(store).list(TENANT_A);
        verify(store).save(TENANT_A, PERSON, "expense", "Expense", "<definitions/>");
    }

    @Test
    void anotherTenantCannotReadOrOverwriteAnExistingDraft() {
        bind(TENANT_A);
        ProcessDefinitionDraft draft = new ProcessDefinitionDraft(
                UUID.randomUUID(), TENANT_A, "expense", "Expense", "<definitions/>", 1, PERSON,
                Instant.now(), Instant.now());
        when(store.find(TENANT_A, "expense")).thenReturn(Optional.of(draft));
        assertEquals(draft, service.get("expense"));

        bind(TENANT_B);
        when(store.find(TENANT_B, "expense")).thenReturn(Optional.empty());
        assertThrows(FlowException.class, () -> service.get("expense"));
        service.save("expense", "Other Tenant Draft", "<definitions id='other'/>");
        verify(store).save(TENANT_B, PERSON, "expense", "Other Tenant Draft", "<definitions id='other'/>");
        Mockito.verify(store, Mockito.never())
                .save(TENANT_A, PERSON, "expense", "Other Tenant Draft", "<definitions id='other'/>");
    }

    private static void bind(UUID tenant) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, UUID.randomUUID(), PERSON, "m6-test", Instant.now(), Instant.now().plusSeconds(60)));
    }

    @Test
    void preservesExclusiveGatewayAndConditionXmlWithoutEvaluation() {
        bind(TENANT_A);
        String xml = "<definitions><process id='expense'><exclusiveGateway id='choice'/>"
                + "<sequenceFlow id='approved' sourceRef='choice' targetRef='done'>"
                + "<conditionExpression><![CDATA[${approved}]]></conditionExpression>"
                + "</sequenceFlow></process></definitions>";
        service.save("expense", "Expense", xml);
        verify(store).save(TENANT_A, PERSON, "expense", "Expense", xml);
    }
}
