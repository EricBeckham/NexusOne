package one.nexus.isolation.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.isolation.spi.FieldPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IsolatedExportTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            registry(), Set.of("ops.summary"), sink);

    private static TableRegistry registry() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "hidden");
        registry.registerTenant("ledger_comment", "p3", 1, "id", "tenant_id", "entry_id", "body");
        return registry;
    }

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

    @Test
    void exportJoinCompileRejectsCrossTenantBeforeSqlWithGuardRecord() {
        bind("alpha");
        RenderedSql same = IsolatedExport.compileQuery(
                "alpha",
                QueryAst.select(
                        "ledger_entry",
                        List.of("id", "ledger_comment.body"),
                        Map.of(),
                        List.of(QueryJoin.inner("ledger_comment", "id", "entry_id"))),
                kernel.compiler());
        assertTrue(same.sql().contains("INNER JOIN"));
        assertEquals(List.of("alpha", "alpha"), same.parameters());

        IsolationException resource = assertThrows(IsolationException.class, () -> IsolatedExport.compileQuery(
                "beta",
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                kernel.compiler()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, resource.error());

        IsolationException join = assertThrows(IsolationException.class, () -> IsolatedExport.compileQuery(
                "alpha",
                QueryAst.select(
                        "ledger_entry",
                        List.of("id", "ledger_comment.body"),
                        Map.of(),
                        List.of(QueryJoin.inner("ledger_comment", "id", "entry_id", Map.of("tenant_id", "beta")))),
                kernel.compiler()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_READ, join.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_READ));
    }

    @Test
    void fieldPolicyOnExportDropsHiddenAndRejectsCrossTenantBeforeSink() {
        bind("alpha");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", "alpha");
        row.put("title", "Alpha");
        row.put("hidden", "secret");
        FieldPolicy policy = source -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", source.get("title"));
            return out;
        };
        List<Map<String, Object>> projected = IsolatedExport.applyFieldPolicy("alpha", List.of(row), policy);
        assertEquals("Alpha", projected.getFirst().get("title"));
        assertFalse(projected.getFirst().containsKey("hidden"));
        String csv = new String(IsolatedExport.csv(projected));
        assertTrue(csv.contains("Alpha"));
        assertFalse(csv.contains("secret"));
        AtomicReference<String> fileKey = new AtomicReference<>();
        IsolatedExport.writeProjected("alpha", "export/rows.csv", List.of(row), policy, fileKey::set);
        assertEquals("tenants/alpha/export/rows.csv", fileKey.get());

        AtomicBoolean sink = new AtomicBoolean(false);
        IsolationException missing = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeProjected("alpha", "export/x.csv", List.of(row), null, key -> sink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_REQUIRED, missing.error());

        IsolationException foreign = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeProjected("beta", "export/x.csv", List.of(row), policy, key -> sink.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, foreign.error());

        Map<String, Object> leaked = new LinkedHashMap<>();
        leaked.put("tenant_id", "beta");
        leaked.put("title", "Beta");
        IsolationException rowTenant = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeProjected("alpha", "export/x.csv", List.of(leaked), policy, key -> sink.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, rowTenant.error());
        assertFalse(sink.get());
        assertTrue(this.sink.rejected(IsolationErrorCode.FIELD_POLICY_REQUIRED));
        assertTrue(this.sink.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
    }

    private void bind(String tenantId) {
        TenantContext context = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId);
        AccessContextHolder.set(context);
    }
}
