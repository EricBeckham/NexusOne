package one.nexus.isolation.context;

import java.util.Optional;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIsolationPolicyTest {
    private IsolationKernel kernel() {
        return IsolationKernel.create(new TableRegistry(), Set.of("ops.summary"), new CollectingAuditSink());
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void issuesFromServerMembershipNotClientHeader() {
        IsolationKernel kernel = kernel();
        AuthenticatedMembership membership = new AuthenticatedMembership(
                Actor.person("user-1"),
                "alpha",
                "mem-1",
                Set.of("alpha", "gamma")
        );
        TenantContext issued = kernel.requests().bindTenant(membership, "alpha", Optional.of("alpha"), "trace-1");
        assertEquals("alpha", issued.tenantId());
        assertEquals("user-1", issued.actor().actorId());
    }

    @Test
    void rejectsClientClaimOutsideMembership() {
        IsolationKernel kernel = kernel();
        AuthenticatedMembership membership = new AuthenticatedMembership(
                Actor.person("user-1"),
                "alpha",
                "mem-1",
                Set.of("alpha")
        );
        IsolationException ex = assertThrows(
                IsolationException.class,
                () -> kernel.requests().bindTenant(membership, "alpha", Optional.of("beta"), "trace-1")
        );
        assertEquals(IsolationErrorCode.CLIENT_TENANT_UNTRUSTED, ex.error());
    }

    @Test
    void authorizedSwitchStillComesFromMembership() {
        IsolationKernel kernel = kernel();
        AuthenticatedMembership membership = new AuthenticatedMembership(
                Actor.person("user-1"),
                "alpha",
                "mem-1",
                Set.of("alpha", "gamma")
        );
        TenantContext switched = kernel.requests().bindTenant(membership, "alpha", Optional.of("gamma"), "trace-2");
        assertEquals("gamma", switched.tenantId());
    }

    @Test
    void missingSessionTenantIsRejected() {
        IsolationKernel kernel = kernel();
        AuthenticatedMembership membership = new AuthenticatedMembership(
                Actor.person("user-1"),
                "alpha",
                "mem-1",
                Set.of("alpha")
        );
        IsolationException ex = assertThrows(
                IsolationException.class,
                () -> kernel.requests().bindTenant(membership, null, Optional.of("alpha"), "trace-1")
        );
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
    }

    @Test
    void illegalPlatformPathIsAudited() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("ops.summary"), sink);
        IsolationException ex = assertThrows(
                IsolationException.class,
                () -> kernel.platform().issue("ops.other", Actor.platformAdmin("admin"), "report", "t-1")
        );
        assertEquals(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, ex.error());
        assertTrue(sink.rejectCount() >= 1);
        kernel.platform().issue("ops.summary", Actor.platformAdmin("admin"), "operations report", "t-1");
        assertTrue(sink.events().stream().anyMatch(event -> "PLATFORM_CONTEXT_ISSUED".equals(event.type())));
    }
}
