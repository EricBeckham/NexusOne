package one.nexus.p1.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import org.junit.jupiter.api.Test;

class PlatformContextAuditTenantTest {
    private static final UUID HOME = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private static final UUID PLATFORM = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void prefersActorHomeTenantThenFallsBackToPlatformTenant() {
        assertEquals(HOME, context(HOME).auditTenantId(PLATFORM));
        assertEquals(PLATFORM, context(null).auditTenantId(PLATFORM));
        assertThrows(IllegalStateException.class, () -> context(null).auditTenantId(null));
    }

    private static PlatformContext context(UUID home) {
        return new PlatformContext(true, "j1", new Actor(UUID.randomUUID(), home, "admin", Set.of(PlatformDuty.SYSTEM_ADMIN)),
                "trace", home);
    }
}
