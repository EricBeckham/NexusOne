package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.junit.jupiter.api.Test;

class PlatformServiceStatusControllerTest {
    @Test
    void requiresSystemAdminPlatformContextBeforeProbing() {
        var isolation = org.mockito.Mockito.mock(IsolationGuard.class);
        var statuses = org.mockito.Mockito.mock(PlatformServiceStatusService.class);
        var snapshot = new PlatformServiceStatusService.Snapshot(java.util.List.of(), java.time.Instant.EPOCH);
        org.mockito.Mockito.when(statuses.snapshot()).thenReturn(snapshot);
        var controller = new PlatformServiceStatusController(isolation, statuses);

        assertSame(snapshot, controller.get());
        org.mockito.Mockito.verify(isolation).requirePlatformContext("platform.service.status", PlatformDuty.SYSTEM_ADMIN);
        org.mockito.Mockito.verify(statuses).snapshot();
    }

    @Test
    void denialStopsTheProbe() {
        var isolation = org.mockito.Mockito.mock(IsolationGuard.class);
        var statuses = org.mockito.Mockito.mock(PlatformServiceStatusService.class);
        org.mockito.Mockito.when(isolation.requirePlatformContext("platform.service.status", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new SecurityException("denied"));
        var controller = new PlatformServiceStatusController(isolation, statuses);

        assertThrows(SecurityException.class, controller::get);
        org.mockito.Mockito.verifyNoInteractions(statuses);
    }
}
