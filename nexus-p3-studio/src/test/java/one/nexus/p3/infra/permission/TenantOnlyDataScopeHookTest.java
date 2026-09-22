package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantOnlyDataScopeHookTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextUsesIsolationOnlyAllow() {
        TenantOnlyDataScopeHook hook = new TenantOnlyDataScopeHook();
        ScopedRead scoped = hook.bind("tenant-a", "ledger", "read");
        assertEquals(IsolationOnlyDecision.REASON, scoped.decision().reason());
        assertTrue(scoped.decision().allowed());
        assertTrue(scoped.query().whereSql().contains("tenant_id"));
    }

    @Test
    void leftoverAllowWithIssuedContextIsDocumentedNoEngineSkip() {
        bindIssued();
        TenantOnlyDataScopeHook hook = new TenantOnlyDataScopeHook();
        ScopedRead scoped = hook.bind("tenant-a", "ledger", "read");
        assertEquals(IsolationOnlyDecision.REASON, scoped.decision().reason());
        assertTrue(scoped.decision().allowed());
        PermissionException forbidden = assertThrows(PermissionException.class, IsolationOnlyDecision::allow);
        assertEquals(IsolationOnlyDecision.FORBIDDEN, forbidden.getMessage());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "iso-hook", Instant.now(), Instant.now().plusSeconds(60)));
    }
}
