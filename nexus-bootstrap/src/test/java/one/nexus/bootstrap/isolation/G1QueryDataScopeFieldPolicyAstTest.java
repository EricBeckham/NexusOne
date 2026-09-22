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
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.IsolationDataScopeInjector;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.QueryAstPolicyCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: compile SELF/DEPT DataScope and FieldPolicy visibility into the
 * Query AST. Does not invent a second tenant context and does not change
 * same-TX {@code AuditPort.append} joining.
 */
class G1QueryDataScopeFieldPolicyAstTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;
    private final IsolationKernel scopedKernel;

    G1QueryDataScopeFieldPolicyAstTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id", "department_id", "phone", "secret");
        IsolationAuditSink audit = IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {}));
        this.kernel = IsolationKernel.create(registry, Set.of("ops.summary"), audit);
        this.scopedKernel = IsolationKernel.create(
                registry,
                Set.of("ops.summary"),
                audit,
                new IsolationDataScopeInjector(Map.of("owner_id", "user-alpha")),
                (q, t, c) -> q);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void selfDeptAndVisibleProjectionCompileIntoAstAndExportDropsHidden() {
        bind("alpha");
        AtomicBoolean compileRanPastGuard = new AtomicBoolean(false);
        Map<String, FieldRule> fields = Map.of(
                "id", new FieldRule(true, true),
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                "secret", new FieldRule(false, false));
        Decision self = new Decision(true, "self", Predicate.atom("owner_id", "=", "user-alpha"), fields, 1L);
        QueryAstPolicyCompiler.Encoding encoded = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id", "title", "phone", "secret"), Map.of()),
                self,
                scopedKernel.registry().require("ledger_entry"));

        assertEquals("user-alpha", encoded.query().filters().get("owner_id"));
        assertFalse(encoded.query().filters().containsKey("tenant_id"));
        assertEquals(List.of("id", "title", "phone"), encoded.query().projection());
        assertTrue(encoded.scopeFullyEncoded());

        RenderedSql compiled = kernel.compiler().compile(encoded.query(), AccessContextHolder.current());
        assertEquals(
                "SELECT \"id\", \"title\", \"phone\" FROM \"ledger_entry\""
                        + " WHERE \"owner_id\" = ? AND \"tenant_id\" = ?",
                compiled.sql());
        assertEquals(List.of("user-alpha", "alpha"), compiled.parameters());

        RenderedSql injected = scopedKernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id", "title"), Map.of()),
                AccessContextHolder.current());
        assertTrue(injected.sql().contains("\"tenant_id\" = ?"));
        assertTrue(injected.sql().contains("\"owner_id\" = ?"));
        assertEquals(List.of("alpha", "user-alpha"), injected.parameters());

        Decision dept = new Decision(true, "dept", Predicate.atom("department_id", "=", "d1"), Map.of(), 1L);
        RenderedSql deptSql = kernel.compiler().compile(
                QueryAstPolicyCompiler.encode(
                        QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                        dept,
                        kernel.registry().require("ledger_entry")).query(),
                AccessContextHolder.current());
        assertTrue(deptSql.sql().contains("\"department_id\" = ?"));
        assertTrue(deptSql.sql().contains("\"tenant_id\" = ?"));

        var dropped = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("id", "title", "secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false);
        compileRanPastGuard.set(true);
        assertFalse(dropped.sql().contains("secret"));
        assertTrue(dropped.sql().startsWith("SELECT \"id\", \"title\" FROM"));

        IsolationException hiddenOnly = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hiddenOnly.error());

        var allowed = IsolatedExport.compile(
                kernel.compiler(),
                encoded.query().withProjection(List.of("id", "title")),
                "alpha",
                ExportFieldPolicies.from(fields),
                false);
        assertFalse(allowed.sql().contains("secret"));
        assertFalse(allowed.sql().contains("phone"));
        assertTrue(allowed.sql().contains("\"owner_id\" = ?"));

        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "user-alpha"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstPolicyCompiler.Encoding encodedOr = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                orScope,
                Map.of(),
                kernel.registry().require("ledger_entry"));
        assertTrue(encodedOr.scopeFullyEncoded());
        RenderedSql orSql = kernel.compiler().compile(encodedOr.query(), AccessContextHolder.current());
        assertTrue(orSql.sql().contains("(\"owner_id\" = ? OR \"department_id\" = ?)"));
        assertTrue(orSql.sql().contains("\"tenant_id\" = ?"));
        assertEquals(List.of("alpha", "user-alpha", "d1"), orSql.parameters());

        Predicate leftoverScope = Predicate.anyOf(
                Predicate.atom("title", "=", "x"),
                Predicate.atom("phone", "=", "1"));
        QueryAstPolicyCompiler.Encoding leftover = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                leftoverScope,
                Map.of(),
                kernel.registry().require("ledger_entry"));
        assertFalse(leftover.scopeFullyEncoded());
        var bound = PermissionQueryBinder.bind("alpha", leftover.leftoverScope());
        assertTrue(bound.whereSql().startsWith("tenant_id = ? AND "));
        assertTrue(bound.whereSql().contains("title"));

        assertTrue(encoded.leftoverMaskFields().containsKey("phone"));
        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("title", "Alpha", "phone", "13812345678", "secret", "classified"),
                fields,
                false);
        assertEquals("***", projected.get("phone"));
        assertFalse(projected.containsKey("secret"));

        assertTrue(compileRanPastGuard.get());
        assertTrue(collecting.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.021".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
