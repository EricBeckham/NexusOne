package one.nexus.event.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionIsolationTest {
    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void restoreUsesRequestIsolationPolicyNotASecondHolder() {
        CollectingAuditSink sink = new CollectingAuditSink();
        ExecutionIsolation isolation = ExecutionIsolation.createForTests(sink);
        TenantContext context = isolation.restoreTenant(
                new StoredMembership("alpha", "user-alpha", Actor.PERSON, "mem-alpha"),
                "trace-bind");
        AccessContextHolder.set(context);
        assertEquals("alpha", isolation.requireBound().boundTenantId().orElseThrow());
        assertEquals("trace-bind", isolation.requireBound().traceId());
        assertEquals(isolation.kernel().requests(), isolation.requests());
    }

    @Test
    void clientTenantHintOutsideMembershipIsRejectedByM002() {
        CollectingAuditSink sink = new CollectingAuditSink();
        ExecutionIsolation isolation = ExecutionIsolation.createForTests(sink);
        AuthenticatedMembership membership = new AuthenticatedMembership(
                Actor.person("user-alpha"), "alpha", "mem-alpha", Set.of("alpha"));
        IsolationException ex = assertThrows(IsolationException.class, () ->
                isolation.requests().bindTenant(membership, "alpha", Optional.of("beta"), "trace-hint"));
        assertEquals(IsolationErrorCode.CLIENT_TENANT_UNTRUSTED, ex.error());
        assertEquals(1, sink.rejectCount());
    }
}
