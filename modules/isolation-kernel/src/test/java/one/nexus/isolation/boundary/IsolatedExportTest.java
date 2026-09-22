package one.nexus.isolation.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.JoinOn;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
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

    @Test
    void exportFieldPolicyRejectsHiddenAndSecondAuthBeforeCompileOrFile() {
        bind("alpha");
        AtomicBoolean fileSink = new AtomicBoolean(false);
        IsolationException hidden = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("id", "secret"), Map.of()),
                "alpha",
                Map.of("id", ExportFieldRule.allow(), "secret", ExportFieldRule.hidden()),
                false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hidden.error());
        IsolationException missing = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                "alpha",
                Map.of(),
                false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, missing.error());
        IsolationException second = assertThrows(IsolationException.class, () -> IsolatedExport.writeProjected(
                "alpha",
                "export/x.csv",
                List.of("phone"),
                List.of(Map.of("phone", "13812345678")),
                Map.of("phone", ExportFieldRule.requireSecondAuthorization()),
                false,
                (key, body) -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, second.error());
        IsolationException hiddenFile = assertThrows(IsolationException.class, () -> IsolatedExport.writeProjected(
                "alpha",
                "export/x.csv",
                List.of("secret"),
                List.of(Map.of("secret", "classified")),
                Map.of("secret", ExportFieldRule.hidden()),
                false,
                (key, body) -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hiddenFile.error());
        assertFalse(fileSink.get());
        assertTrue(sink.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        assertTrue(sink.rejectCount() >= 4);
    }

    @Test
    void sameTenantExportFieldPolicyCompilesJoinAndWritesProjectedCsv() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
        IsolationKernel tables = IsolationKernel.create(registry, Set.of("ops.summary"), sink);
        TenantContext context = tables.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-alpha"), "alpha", "mem-alpha", Set.of("alpha")), "trace-alpha");
        AccessContextHolder.set(context);
        Map<String, ExportFieldRule> policy = Map.of(
                "id", ExportFieldRule.allow(),
                "amount", ExportFieldRule.allow());
        var sql = IsolatedExport.compile(
                tables.compiler(),
                QueryAst.select("ledger_entry", List.of("ledger_entry.id", "line.amount"), Map.of(), List.of(
                        QueryJoin.inner("ledger_line", "line", List.of(
                                new JoinOn("ledger_entry", "id", "line", "entry_id"))))),
                "alpha",
                policy,
                false);
        assertTrue(sql.sql().contains("INNER JOIN"));
        assertTrue(sql.sql().contains("\"line\".\"tenant_id\" = ?"));
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> csv = new AtomicReference<>();
        IsolatedExport.writeProjected(
                "alpha",
                "export/ok.csv",
                List.of("id", "amount"),
                List.of(Map.of("id", "a1", "amount", "10")),
                policy,
                false,
                (objectKey, body) -> {
                    key.set(objectKey);
                    csv.set(new String(body));
                });
        assertEquals("tenants/alpha/export/ok.csv", key.get());
        assertTrue(csv.get().contains("a1,10"));
        assertFalse(sink.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        assertFalse(sink.rejected(IsolationErrorCode.CROSS_TENANT_JOIN));
    }

    private void bind(String tenantId) {
        TenantContext context = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId);
        AccessContextHolder.set(context);
    }
}
