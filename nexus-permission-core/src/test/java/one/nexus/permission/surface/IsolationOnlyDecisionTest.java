package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Isolation-only {@code tenant_only} is allow only with no P2
 * {@code TenantContext}. An issued context fail-closes.
 */
class IsolationOnlyDecisionTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextAllowsTenantOnly() {
        assertTrue(IsolationOnlyDecision.isolationOnly());
        Decision decision = IsolationOnlyDecision.allow();
        assertTrue(decision.allowed());
        assertEquals(IsolationOnlyDecision.REASON, decision.reason());
        assertEquals("true", decision.scope().op());
    }

    @Test
    void issuedP2ContextFailClosesAndCannotEmitTenantOnly() {
        bindIssued();
        assertFalse(IsolationOnlyDecision.isolationOnly());
        PermissionException ex = assertThrows(PermissionException.class, IsolationOnlyDecision::allow);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals(IsolationOnlyDecision.FORBIDDEN, ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "iso-only", Instant.now(), Instant.now().plusSeconds(60)));
    }
}
