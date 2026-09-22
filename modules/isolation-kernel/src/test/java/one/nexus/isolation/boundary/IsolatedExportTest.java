package one.nexus.isolation.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

class IsolatedExportTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("ops.summary"), sink);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void sameTenantExportRunsQueryFileCacheAndTaskGuards() {
        bind("alpha");
        IsolatedEnvelope envelope = IsolatedExport.submit("alpha", "{\"ledger\":\"leave\"}");
        assertEquals(IsolatedExport.TASK_TYPE, envelope.payloadType());
        AtomicReference<String> fileKey = new AtomicReference<>();
        AtomicReference<String> cacheKey = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean(false);
        IsolatedExport.dispatch(envelope, "alpha", item -> ran.set(true));
        IsolatedExport.writeFile("alpha", "export/2026/note.csv", "tenant,alpha\n".getBytes(), fileKey::set);
        IsolatedExport.cacheStatus("alpha", "dev", "job-1", "PENDING", (key, value) -> cacheKey.set(key));
        assertTrue(ran.get());
        assertEquals("tenants/alpha/export/2026/note.csv", fileKey.get());
        assertEquals("nexus:dev:alpha:export:job-1", cacheKey.get());
        assertEquals(0, sink.rejectCount());
    }

    @Test
    void crossTenantExportIsRejectedBeforeFileCacheOrTaskBody() {
        bind("alpha");
        AtomicBoolean fileSink = new AtomicBoolean(false);
        AtomicBoolean cacheSink = new AtomicBoolean(false);
        AtomicBoolean taskBody = new AtomicBoolean(false);
        IsolationException submit = assertThrows(IsolationException.class,
                () -> IsolatedExport.submit("beta", "{}"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, submit.error());
        IsolationException file = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeFile("beta", "export/x.csv", new byte[] {1}, key -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, file.error());
        IsolationException cache = assertThrows(IsolationException.class,
                () -> IsolatedExport.cacheStatus("beta", "dev", "job-1", "PENDING", (k, v) -> cacheSink.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, cache.error());
        IsolatedEnvelope stolen = new IsolatedEnvelope("beta", "trace-stolen", IsolatedExport.TASK_TYPE, "{}");
        IsolationException dispatch = assertThrows(IsolationException.class,
                () -> IsolatedExport.dispatch(stolen, "beta", item -> taskBody.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, dispatch.error());
        assertFalse(fileSink.get());
        assertFalse(cacheSink.get());
        assertFalse(taskBody.get());
        assertTrue(sink.rejectCount() >= 4);
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
        assertTrue(sink.rejects().stream().allMatch(event ->
                event.traceId() != null && !event.traceId().isBlank()));
    }

    private void bind(String tenantId) {
        TenantContext context = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId);
        AccessContextHolder.set(context);
    }
}
