package one.nexus.p3.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
