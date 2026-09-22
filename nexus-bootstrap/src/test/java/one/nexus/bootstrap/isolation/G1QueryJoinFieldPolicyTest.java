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
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.field.FieldPolicyProjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 remaining: Query AST join isolation + FieldPolicy-on-export reject
 * before execute, with IsolationAuditEvent + AuditPort GUARD_REJECT.
 * Does not change same-TX {@code AuditPort.append} joining.
 */
class G1QueryJoinFieldPolicyTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryJoinFieldPolicyTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "phone", "secret");
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
    void joinAndExportFieldPolicyRejectsLeaveGuardRecordsAndNeverRunSinks() {
        bind("alpha");
        AtomicBoolean fileSink = new AtomicBoolean(false);
        AtomicBoolean compileRanPastGuard = new AtomicBoolean(false);

        IsolationException join = assertThrows(IsolationException.class, () -> {
            kernel.compiler().compile(stolenJoin(), AccessContextHolder.current());
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, join.error());

        IsolationException exportJoin = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                stolenJoin(),
                "alpha",
                Map.of("id", ExportFieldRule.allow()),
                false));
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, exportJoin.error());

        Map<String, FieldRule> fields = Map.of(
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                "secret", new FieldRule(false, false));
        var dropped = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("title", "secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false);
        assertTrue(dropped.sql().startsWith("SELECT \"title\" FROM \"ledger_entry\""));
        assertFalse(dropped.sql().contains("secret"));

        IsolationException hiddenOnly = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hiddenOnly.error());

        IsolationException second = assertThrows(IsolationException.class, () -> IsolatedExport.writeProjected(
                "alpha",
                "export/x.csv",
                List.of("phone"),
                List.of(Map.of("phone", "13812345678")),
                ExportFieldPolicies.from(fields),
                false,
                (key, body) -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, second.error());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", "Alpha");
        row.put("phone", "13812345678");
        row.put("secret", "classified");
        Map<String, Object> projected = FieldPolicyProjector.project(row, fields, false);
        IsolatedExport.writeProjected(
                "alpha",
                "export/ok.csv",
                List.of("title"),
                List.of(projected),
                ExportFieldPolicies.from(fields),
                false,
                (key, body) -> {
                    assertEquals("tenants/alpha/export/ok.csv", key);
                    assertTrue(new String(body).contains("Alpha"));
                    assertFalse(new String(body).contains("classified"));
                    assertFalse(new String(body).contains("13812345678"));
                });

        assertFalse(compileRanPastGuard.get());
        assertFalse(fileSink.get());
        assertFalse(projected.containsKey("secret"));
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_JOIN));
        assertTrue(collecting.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        assertTrue(collecting.rejectCount() >= 4);

        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.020".equals(event.event().resourceId())));
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.021".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private static QueryAst stolenJoin() {
        return QueryAst.select(
                "ledger_entry",
                List.of("id"),
                Map.of(),
                List.of(QueryJoin.inner("ledger_line", "line", List.of(
                        new JoinOn("ledger_entry", "id", "line", "entry_id")),
                        Map.of("tenant_id", "beta"))));
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
