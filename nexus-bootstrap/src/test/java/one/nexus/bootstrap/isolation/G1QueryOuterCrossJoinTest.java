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
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 remaining Query AST: OUTER JOIN / CROSS JOIN reject before execute, with
 * IsolationAuditEvent + AuditPort GUARD_REJECT. Does not change same-TX
 * {@code AuditPort.append} joining.
 */
class G1QueryOuterCrossJoinTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryOuterCrossJoinTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
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
    void outerAndCrossJoinRejectsLeaveGuardRecordsAndNeverRunSinks() {
        bind("alpha");
        AtomicBoolean compileRanPastGuard = new AtomicBoolean(false);

        IsolationException stolenLeft = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(stolenLeftJoin(), AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, stolenLeft.error());

        IsolationException exportLeft = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                stolenLeftJoin(),
                "alpha",
                Map.of("id", ExportFieldRule.allow()),
                false));
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, exportLeft.error());

        IsolationException stolenCross = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(stolenCrossJoin(), AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, stolenCross.error());

        IsolationException illegalOuter = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(
                    QueryAst.select("ledger_entry", List.of("id"), Map.of(), List.of(
                            QueryJoin.of(QueryJoin.Kind.LEFT, "ledger_line", "line", List.of(), Map.of()))),
                    AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, illegalOuter.error());

        IsolationException illegalCross = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(
                    QueryAst.select("ledger_entry", List.of("id"), Map.of(), List.of(
                            QueryJoin.of(
                                    QueryJoin.Kind.CROSS,
                                    "ledger_line",
                                    "line",
                                    List.of(new JoinOn("ledger_entry", "id", "line", "entry_id")),
                                    Map.of()))),
                    AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, illegalCross.error());

        var allowedLeft = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("ledger_entry.id", "line.amount"), Map.of(), List.of(
                        QueryJoin.left("ledger_line", "line", List.of(
                                new JoinOn("ledger_entry", "id", "line", "entry_id"))))),
                "alpha",
                Map.of("id", ExportFieldRule.allow(), "amount", ExportFieldRule.allow()),
                false);
        assertTrue(allowedLeft.sql().contains("LEFT OUTER JOIN"));
        assertTrue(allowedLeft.sql().contains("\"line\".\"tenant_id\" = ?"));

        var allowedCross = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("ledger_entry.id", "line.amount"), Map.of(), List.of(
                        QueryJoin.cross("ledger_line", "line"))),
                "alpha",
                Map.of("id", ExportFieldRule.allow(), "amount", ExportFieldRule.allow()),
                false);
        assertTrue(allowedCross.sql().contains("CROSS JOIN"));
        assertTrue(allowedCross.sql().contains("\"line\".\"tenant_id\" = ?"));

        assertFalse(compileRanPastGuard.get());
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_JOIN));
        assertTrue(collecting.rejectCount() >= 5);

        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.020".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private static QueryAst stolenLeftJoin() {
        return QueryAst.select(
                "ledger_entry",
                List.of("id"),
                Map.of(),
                List.of(QueryJoin.left("ledger_line", "line", List.of(
                        new JoinOn("ledger_entry", "id", "line", "entry_id")),
                        Map.of("tenant_id", "beta"))));
    }

    private static QueryAst stolenCrossJoin() {
        return QueryAst.select(
                "ledger_entry",
                List.of("id"),
                Map.of(),
                List.of(QueryJoin.cross("ledger_line", "line", Map.of("tenant_id", "beta"))));
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
