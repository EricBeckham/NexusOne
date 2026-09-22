package one.nexus.common.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import one.nexus.common.id.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {
    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void tenantCodeMustBeStableIdentifier() {
        UUID id = UuidV7.generate();
        assertEquals("acme", new TenantContext(id, "acme").tenantCode());
        assertThrows(IllegalArgumentException.class, () -> new TenantContext(id, "Acme"));
        assertThrows(IllegalArgumentException.class, () -> new TenantContext(id, ""));
    }

    @Test
    void platformContextRequiresActorReasonAndTrace() {
        Actor actor = new Actor(UuidV7.generate(), ActorType.USER, "ops");
        var ctx = new PlatformContext("platform.reconcile", actor, "month-end", "trace-1");
        assertEquals("platform.reconcile", ctx.path());
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformContext("platform.reconcile", actor, " ", "trace-1"));
    }

    @Test
    void requestContextIsThreadLocal() {
        Actor actor = new Actor(UuidV7.generate(), ActorType.SYSTEM, "job");
        TenantContext tenant = new TenantContext(UuidV7.generate(), "acme");
        RequestContext.set(new RequestContext.Holder("t1", actor, tenant, null));
        assertTrue(RequestContext.tenant().isPresent());
        assertEquals("t1", RequestContext.traceIdOr("missing"));
        RequestContext.clear();
        assertTrue(RequestContext.current().isEmpty());
    }
}
