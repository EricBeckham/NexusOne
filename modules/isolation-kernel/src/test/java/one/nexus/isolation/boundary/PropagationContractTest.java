package one.nexus.isolation.boundary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagationContractTest {
    private record KernelSink(IsolationKernel kernel, CollectingAuditSink sink) {}

    private KernelSink kernel() {
        CollectingAuditSink sink = new CollectingAuditSink();
        return new KernelSink(
                IsolationKernel.create(new TableRegistry(), Set.of("ops.summary"), sink),
                sink);
    }

    private TenantContext tenant(IsolationKernel kernel, String tenantId) {
        return kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)), "trace-" + tenantId);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void asyncTaskCarriesCapturedContext() {
        IsolationKernel kernel = kernel().kernel();
        TenantContext alpha = tenant(kernel, "alpha");
        AccessContextHolder.set(alpha);
        List<String> seen = new ArrayList<>();
        Runnable wrapped = IsolatedExecutor.wrap(() -> seen.add(AccessContextHolder.current().boundTenantId().orElseThrow()));
        AccessContextHolder.clear();
        wrapped.run();
        assertEquals(List.of("alpha"), seen);
    }

    @Test
    void asyncSubmitWithoutContextIsRejectedBeforeSideEffect() {
        AtomicBoolean ran = new AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () -> IsolatedExecutor.wrap(() -> ran.set(true)));
        assertEquals(IsolationErrorCode.MISSING_CONTEXT, ex.error());
        assertFalse(ran.get());
    }

    @Test
    void cachePutRejectsForeignKeyBeforeSink() {
        KernelSink created = kernel();
        AccessContextHolder.set(tenant(created.kernel(), "alpha"));
        AtomicBoolean written = new AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:row"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, ex.error());
        assertTrue(created.sink().rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        CacheKeyGuard.put("dev", "p3", "row", "v", (k, v) -> written.set(true));
        assertTrue(written.get());
        assertEquals("nexus:dev:alpha:p3:row", CacheKeyGuard.key("dev", AccessContextHolder.current(), "p3", "row"));
    }

    @Test
    void fileKeyRejectsCrossTenantBeforeSink() {
        KernelSink created = kernel();
        AccessContextHolder.set(tenant(created.kernel(), "alpha"));
        AtomicBoolean stored = new AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), "tenants/beta/doc.pdf"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, ex.error());
        assertTrue(created.sink().rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        FileObjectKeyGuard.putObject("docs/a.pdf", new byte[] {1}, key -> stored.set(true));
        assertTrue(stored.get());
        assertEquals("tenants/alpha/docs/a.pdf", FileObjectKeyGuard.objectKey(AccessContextHolder.current(), "docs/a.pdf"));
    }

    @Test
    void outboxDispatchRejectsCrossTenantBeforeDispatcher() {
        KernelSink created = kernel();
        AccessContextHolder.set(tenant(created.kernel(), "alpha"));
        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent("ledger.created", "{}");
        AtomicBoolean dispatched = new AtomicBoolean(false);
        envelope.dispatch(item -> dispatched.set(true));
        assertTrue(dispatched.get());
        AccessContextHolder.set(tenant(created.kernel(), "beta"));
        AtomicBoolean again = new AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class, () -> envelope.dispatch(item -> again.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, ex.error());
        assertFalse(again.get());
        assertTrue(created.sink().rejected(IsolationErrorCode.CROSS_TENANT_ASYNC));
    }
}
