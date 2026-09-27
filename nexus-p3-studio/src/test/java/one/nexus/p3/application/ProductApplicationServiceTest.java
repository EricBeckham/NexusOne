package one.nexus.p3.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ApprovalCommentStore;
import one.nexus.p3.flow.SequentialApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import one.nexus.p3.port.ProductApplicationStore;
import tools.jackson.databind.ObjectMapper;

class ProductApplicationServiceTest {
    private final ProductApplicationStore store = mock(ProductApplicationStore.class);
    private final PinnedProductApplicationValidationService validation = mock(PinnedProductApplicationValidationService.class);
    private final SequentialApprovalService approvals = mock(SequentialApprovalService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AuditPort> audit = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ApprovalCommentStore> comments = mock(ObjectProvider.class);
    private final ProductApplicationService service = new ProductApplicationService(
            store, new ObjectMapper(), validation, approvals, audit, comments);

    @AfterEach void clearContexts() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void submissionUsesExactDefaultOrTenantOverrideWithoutRewritingGlobalPin(boolean customized) throws Exception {
        UUID tenant = UUID.randomUUID(), installation = UUID.randomUUID(), form = UUID.randomUUID(), product = UUID.randomUUID();
        AccessContextHolder.set(new TenantContext(tenant.toString(), Actor.person("member"), "membership", "trace", Instant.now()));
        TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(UUID.randomUUID(), tenant, UUID.randomUUID(),
                UUID.randomUUID(), "subject", Instant.now(), Instant.now().plusSeconds(60)));
        when(validation.validate(eq(installation), any())).thenReturn(new PinnedProductApplicationValidationService.ValidationResult(
                installation, product, form, true, List.of()));
        when(store.findInstalledPin(tenant, installation)).thenReturn(java.util.Optional.of(
                new ProductApplicationStore.InstalledPin(form, product, "platform-expense", 2)));
        when(store.findProcessOverride(tenant, installation)).thenReturn(customized ? java.util.Optional.of(
                new ProductApplicationStore.ProcessOverride("tenant-expense", 4)) : java.util.Optional.empty());
        when(store.formTemplateSnapshot(form)).thenReturn(java.util.Optional.of("[{\"key\":\"approver\",\"type\":\"PERSON\"}]"));
        doThrow(new IllegalStateException("stop before engine side effects")).when(approvals).startProduct(any(), anyInt(), anyBoolean());
        assertThrows(IllegalStateException.class, () -> service.create(installation,
                new ObjectMapper().readTree("{\"approver\":\"00000000-0000-0000-0000-000000000001\"}")));
        verify(approvals).startProduct(argThat(command -> command.processKey().equals(customized ? "tenant-expense" : "platform-expense")),
                eq(customized ? 4 : 2), eq(customized));
        verify(approvals, org.mockito.Mockito.never()).start(any(), any());
    }

    @Test void invalidValuesDoNotCreateApplicationOrStartApproval() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TenantContext access = new TenantContext(tenantId.toString(), Actor.person("member-1"), "membership-1",
                "trace-1", Instant.now());
        AccessContextHolder.set(access);
        TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(UUID.randomUUID(), tenantId,
                UUID.randomUUID(), UUID.randomUUID(), "subject-1", Instant.now(), Instant.now().plusSeconds(60)));
        var errors = List.of(new ProductApplicationValuesValidator.FieldError("name", "field.required", "required"));
        when(validation.validate(org.mockito.ArgumentMatchers.eq(installationId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PinnedProductApplicationValidationService.ValidationResult(
                        installationId, productId, templateId, false, errors));

        assertThrows(ProductApplicationService.InvalidProductApplicationException.class,
                () -> service.create(installationId, new ObjectMapper().readTree("{\"name\":\"\"}")));

        verify(validation).validate(org.mockito.ArgumentMatchers.eq(installationId), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(store, approvals);
    }
}
