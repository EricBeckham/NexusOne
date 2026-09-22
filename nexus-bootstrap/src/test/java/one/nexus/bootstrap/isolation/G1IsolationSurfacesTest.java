package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.CacheKeyGuard;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.isolation.spi.FieldPolicy;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.field.FieldPolicyProjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 remaining surfaces: export / file / cache / Job-Task plus Query AST joins
 * and FieldPolicy-on-export reject-before-execute, with IsolationAuditEvent +
 * AuditPort GUARD_REJECT (appendNewTransaction). Does not change same-TX append.
 */
class G1IsolationSurfacesTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(),
            Set.of("files.reconcile", "cache.ops", "exec.quartz"),
            IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void exportFileCacheAndTaskRejectsLeaveGuardRecordsAndNeverRunSinks() {
        bind("alpha");
        AtomicBoolean exportFile = new AtomicBoolean(false);
        AtomicBoolean file = new AtomicBoolean(false);
        AtomicBoolean cache = new AtomicBoolean(false);
        AtomicBoolean task = new AtomicBoolean(false);

        IsolationException export = assertThrows(IsolationException.class,
                () -> IsolatedExport.submit("beta", "{\"ledger\":\"leave\"}"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, export.error());
        IsolationException exportFileEx = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeFile("beta", "export/x.csv", new byte[] {1}, key -> exportFile.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, exportFileEx.error());

        IsolationException fileEx = assertThrows(IsolationException.class,
                () -> FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), "tenants/beta/docs/a.pdf"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, fileEx.error());
        IsolationException filePut = assertThrows(IsolationException.class, () -> {
            FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), "tenants/beta/docs/a.pdf");
            file.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, filePut.error());

        IsolationException cacheEx = assertThrows(IsolationException.class,
                () -> CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:row"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, cacheEx.error());
        IsolationException cachePut = assertThrows(IsolationException.class, () -> {
            CacheKeyGuard.assertMatches(AccessContextHolder.current(), "nexus:dev:beta:p3:row");
            cache.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_CACHE, cachePut.error());

        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent("export.csv", "{}");
        AccessContextHolder.clear();
        bind("beta");
        IsolationException taskEx = assertThrows(IsolationException.class,
                () -> envelope.dispatch(item -> task.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, taskEx.error());

        assertFalse(exportFile.get());
        assertFalse(file.get());
        assertFalse(cache.get());
        assertFalse(task.get());
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_CACHE));
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_ASYNC));
        assertTrue(collecting.rejectCount() >= 4);

        var exportRows = port.findByTraceId("trace-alpha");
        assertTrue(exportRows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.019".equals(event.event().resourceId())));
        assertTrue(exportRows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.015".equals(event.event().resourceId())));
        assertTrue(exportRows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.014".equals(event.event().resourceId())));
        var taskRows = port.findByTraceId("trace-beta");
        assertTrue(taskRows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.016".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    @Test
    void queryJoinAndFieldPolicyExportRejectsLeaveGuardRecords() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "hidden");
        registry.registerTenant("ledger_comment", "p3", 1, "id", "tenant_id", "entry_id", "body");
        CollectingAuditSink local = new CollectingAuditSink();
        InMemoryHashChainedAuditPort audit =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        IsolationKernel joinKernel = IsolationKernel.create(
                registry,
                Set.of("files.reconcile", "cache.ops", "exec.quartz"),
                IsolationAuditSink.tee(local, new AuditPortIsolationSink(audit, event -> {})));
        AccessContextHolder.set(joinKernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("user-alpha"), "alpha", "mem-alpha", Set.of("alpha")),
                "trace-join-export"));

        IsolationException join = assertThrows(IsolationException.class, () -> IsolatedExport.compileQuery(
                "alpha",
                QueryAst.select(
                        "ledger_entry",
                        List.of("id", "ledger_comment.body"),
                        Map.of(),
                        List.of(QueryJoin.inner("ledger_comment", "id", "entry_id", Map.of("tenant_id", "beta")))),
                joinKernel.compiler()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_READ, join.error());

        IsolationException exportJoin = assertThrows(IsolationException.class, () -> IsolatedExport.compileQuery(
                "beta",
                QueryAst.select(
                        "ledger_entry",
                        List.of("id"),
                        Map.of(),
                        List.of(QueryJoin.inner("ledger_comment", "id", "entry_id"))),
                joinKernel.compiler()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, exportJoin.error());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", "alpha");
        row.put("title", "Alpha");
        row.put("hidden", "secret");
        Map<String, FieldRule> fields = Map.of(
                "title", new FieldRule(true, true),
                "hidden", new FieldRule(false, false));
        FieldPolicy policy = source -> FieldPolicyProjector.project(source, fields);
        List<Map<String, Object>> projected = IsolatedExport.applyFieldPolicy("alpha", List.of(row), policy);
        assertEquals("Alpha", projected.getFirst().get("title"));
        assertFalse(projected.getFirst().containsKey("hidden"));

        AtomicBoolean file = new AtomicBoolean(false);
        IsolationException policyExport = assertThrows(IsolationException.class,
                () -> IsolatedExport.writeProjected("beta", "export/x.csv", List.of(row), policy, key -> file.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, policyExport.error());
        assertFalse(file.get());

        assertTrue(local.rejected(IsolationErrorCode.CROSS_TENANT_READ));
        assertTrue(local.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
        var rows = audit.findByTraceId("trace-join-export");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.003".equals(event.event().resourceId())));
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.019".equals(event.event().resourceId())));
        assertTrue(audit.verifyChain().intact());
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
