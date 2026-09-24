package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.junit.jupiter.api.Test;

class PlatformAuditSearchControllerTest {
    @Test
    void requiresAuditAdminDutyBeforeSearchingAnyTenant() {
        var isolation = org.mockito.Mockito.mock(IsolationGuard.class);
        var search = org.mockito.Mockito.mock(PlatformAuditSearchService.class);
        var page = new PlatformAuditSearchService.Result(List.of(), 0, 1, 50);
        org.mockito.Mockito.when(search.search(org.mockito.ArgumentMatchers.any())).thenReturn(page);
        var controller = new PlatformAuditSearchController(isolation, search);

        assertSame(page, controller.list(null, null, UUID.randomUUID(), null, null, null, null, null, 1, 50));
        org.mockito.Mockito.verify(isolation).requirePlatformContext("audit.events.search", PlatformDuty.AUDIT_ADMIN);
        org.mockito.Mockito.verify(search).search(org.mockito.ArgumentMatchers.argThat(filter ->
                filter.tenantId() != null && filter.page() == 1 && filter.size() == 50));
    }

    @Test
    void deniedPlatformContextDoesNotReachSearchService() {
        var isolation = org.mockito.Mockito.mock(IsolationGuard.class);
        var search = org.mockito.Mockito.mock(PlatformAuditSearchService.class);
        org.mockito.Mockito.when(isolation.requirePlatformContext("audit.events.search", PlatformDuty.AUDIT_ADMIN))
                .thenThrow(new SecurityException("denied"));
        var controller = new PlatformAuditSearchController(isolation, search);

        assertThrows(SecurityException.class,
                () -> controller.list(null, null, null, null, null, null, null, null, 1, 50));
        org.mockito.Mockito.verifyNoInteractions(search);
    }
}
