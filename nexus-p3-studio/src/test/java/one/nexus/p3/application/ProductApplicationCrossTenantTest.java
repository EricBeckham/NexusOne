package one.nexus.p3.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import one.nexus.p3.port.ProductApplicationStore;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class ProductApplicationCrossTenantTest {
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID installationB = UUID.randomUUID();
    private final ProductApplicationStore store = mock(ProductApplicationStore.class);
    private final PinnedProductApplicationValidationService validation = mock(PinnedProductApplicationValidationService.class);
    private final SequentialApprovalService approvals = mock(SequentialApprovalService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AuditPort> audit = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ApprovalCommentStore> comments = mock(ObjectProvider.class);

    @AfterEach void clearContexts() {
        AccessContextHolder.clear();
        TenantContextHolder.clear();
    }

    @Test void tenantCannotCreateApplicationForAnotherTenantsInstallation() throws Exception {
        AccessContextHolder.set(new TenantContext(tenantA.toString(), Actor.person("member-a"), "membership-a",
                "trace-a", Instant.now()));
        TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(UUID.randomUUID(), tenantA,
                UUID.randomUUID(), UUID.randomUUID(), "member-a", Instant.now(), Instant.now().plusSeconds(60)));
        when(validation.validate(eq(installationB), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "installed product not found"));
        ProductApplicationService service = new ProductApplicationService(
                store, new ObjectMapper(), validation, approvals, audit, comments);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(installationB, new ObjectMapper().readTree("{\"title\":\"cross-tenant\"}")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(validation).validate(eq(installationB), any());
        verifyNoInteractions(store, approvals);
    }
}
