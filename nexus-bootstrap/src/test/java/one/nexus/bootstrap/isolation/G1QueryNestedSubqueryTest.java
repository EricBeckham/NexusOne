package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.JoinOn;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QuerySubquery;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 remaining Query AST: nested subquery-in-subquery. Every level still
 * carries tenant predicates / TableAccessPlan. Stolen nested tenant is
 * rejected before execute. IsolationAuditEvent + AuditPort GUARD_REJECT.
 * Does not change same-TX {@code AuditPort.append} joining.
 */
class G1QueryNestedSubqueryTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryNestedSubqueryTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
        registry.registerTenant("ledger_note", "p3", 1, "id", "tenant_id", "line_id", "body");
        this.kernel = IsolationKernel.create(
                registry,
                Set.of("ops.summary"),
                IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void nestedSubqueryRejectsLeaveGuardRecordsAndCompilesSameTenantTree() {
        bind("alpha");
        AtomicBoolean compileRanPastGuard = new AtomicBoolean(false);

        IsolationException stolen = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(stolenNested(), AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_SUBQUERY, stolen.error());

        IsolationException exportStolen = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                stolenNested(),
                "alpha",
                Map.of("id", ExportFieldRule.allow()),
                false));
        assertEquals(IsolationErrorCode.CROSS_TENANT_SUBQUERY, exportStolen.error());

        IsolationException nullNested = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(
                    QueryAst.select("ledger_entry", List.of("id"), Map.of())
                            .withSubqueries(List.of(QuerySubquery.in("id", "ledger_line", "entry_id", Map.of())
                                    .withNested(List.of(QuerySubquery.in(
                                            "id", "ledger_note", "line_id", map("tenant_id", null)))))),
                    AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_SUBQUERY, nullNested.error());

        var allowed = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSubqueries(List.of(QuerySubquery.in("id", "ledger_line", "entry_id", Map.of())
                                .withNested(List.of(QuerySubquery.in("id", "ledger_note", "line_id", Map.of()))))),
                "alpha",
                Map.of("id", ExportFieldRule.allow()),
                false);
        assertTrue(allowed.sql().contains("IN (SELECT \"entry_id\" FROM \"ledger_line\""));
        assertTrue(allowed.sql().contains("IN (SELECT \"line_id\" FROM \"ledger_note\""));
        assertTrue(allowed.sql().contains("\"tenant_id\" = ?"));
        assertEquals(3, allowed.parameters().stream().filter("alpha"::equals).count());

        var exists = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSubqueries(List.of(QuerySubquery.exists(
                                        "ledger_line",
                                        "line",
                                        List.of(new JoinOn("ledger_entry", "id", "line", "entry_id")),
                                        Map.of())
                                .withNested(List.of(QuerySubquery.exists(
                                        "ledger_note",
                                        "note",
                                        List.of(new JoinOn("line", "id", "note", "line_id")),
                                        Map.of()))))),
                AccessContextHolder.current());
        assertTrue(exists.sql().contains("EXISTS (SELECT 1 FROM \"ledger_line\" \"line\""));
        assertTrue(exists.sql().contains("EXISTS (SELECT 1 FROM \"ledger_note\" \"note\""));
        assertEquals("TenantContext", exists.plan().tenantSource());

        assertFalse(compileRanPastGuard.get());
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_SUBQUERY));
        assertTrue(collecting.rejectCount() >= 3);

        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.022".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private static QueryAst stolenNested() {
        return QueryAst.select("ledger_entry", List.of("id"), Map.of())
                .withSubqueries(List.of(QuerySubquery.in("id", "ledger_line", "entry_id", Map.of())
                        .withNested(List.of(QuerySubquery.in(
                                "id", "ledger_note", "line_id", Map.of("tenant_id", "beta"))))));
    }

    private static Map<String, Object> map(String key, Object value) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
