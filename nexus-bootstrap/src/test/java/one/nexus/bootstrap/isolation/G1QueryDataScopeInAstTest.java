package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.DataScopeCompiler;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.QueryAstPolicyCompiler;
import one.nexus.permission.scope.QueryAstScopedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: encode single-column multi-value IN (DEPT_AND_CHILDREN /
 * SPECIFIED) and mixed-column SELF∨DEPT OR into the Query AST. Empty IN and
 * unencodable leftovers stay fail-closed on {@link PermissionQueryBinder}.
 * Does not invent a second tenant context and does not change same-TX
 * {@code AuditPort.append} joining. Does not claim product G1 closed.
 */
class G1QueryDataScopeInAstTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryDataScopeInAstTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id", "department_id");
        IsolationAuditSink audit = IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {}));
        this.kernel = IsolationKernel.create(registry, Set.of("ops.summary"), audit);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void deptAndChildrenInAndSelfOrDeptCompileThroughQueryCompiler() {
        bind("alpha");
        Subject subject = new Subject("alpha", "user-alpha", Set.of(), Set.of(), Set.of("d1"),
                Map.of("d1", "/d1/", "d2", "/d1/d2/"));
        DepartmentCatalog catalog = new DepartmentCatalog(Map.of(
                "d1", "/d1/",
                "d2", "/d1/d2/",
                "d9", "/other/"));
        Predicate children = DataScopeCompiler.compile(DataScopeKind.DEPT_AND_CHILDREN, subject, catalog, List.of());
        Decision decision = new Decision(true, "dept_and_children", children, Map.of(), 1L);
        QueryAstScopedQuery.CompiledRead compiled = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id", "title"), Map.of()),
                decision,
                kernel.registry().require("ledger_entry"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(compiled.scopeFullyEncoded());
        assertEquals(List.of("d1", "d2"), compiled.encoding().query().filters().get("department_id"));
        assertFalse(compiled.encoding().query().filters().containsKey("tenant_id"));
        RenderedSql sql = compiled.compiled();
        assertTrue(sql.sql().contains("\"department_id\" IN (?, ?)"));
        assertTrue(sql.sql().contains("\"tenant_id\"::text = ?"));
        assertEquals(sql.sql(), compiled.bound().sql());
        assertEquals(List.of("d1", "d2", "alpha"), sql.parameters());

        Predicate specified = DataScopeCompiler.compile(
                DataScopeKind.SPECIFIED, subject, catalog, List.of("d1", "d9"));
        QueryAstPolicyCompiler.Encoding encodedSpecified = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                specified,
                Map.of(),
                kernel.registry().require("ledger_entry"));
        assertTrue(encodedSpecified.scopeFullyEncoded());
        assertEquals(Set.of("d1", "d9"), Set.copyOf((List<?>) encodedSpecified.query().filters().get("department_id")));

        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "user-alpha"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstScopedQuery.CompiledRead compiledOr = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                new Decision(true, "self_or_dept", orScope, Map.of(), 1L),
                kernel.registry().require("ledger_entry"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(compiledOr.scopeFullyEncoded());
        assertEquals(1, compiledOr.encoding().query().disjunctions().size());
        assertEquals(
                List.of(Map.of("owner_id", "user-alpha"), Map.of("department_id", "d1")),
                compiledOr.encoding().query().disjunctions().getFirst().branches());
        assertTrue(compiledOr.compiled().sql().contains("(\"owner_id\" = ? OR \"department_id\" = ?)"));
        assertTrue(compiledOr.compiled().sql().contains("\"tenant_id\"::text = ?"));
        assertEquals(compiledOr.compiled().sql(), compiledOr.bound().sql());
        assertEquals(List.of("alpha", "user-alpha", "d1"), compiledOr.compiled().parameters());

        QueryAstPolicyCompiler.Encoding emptyIn = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.atom("department_id", "IN", List.of()),
                Map.of(),
                kernel.registry().require("ledger_entry"));
        assertFalse(emptyIn.scopeFullyEncoded());
        var emptyBound = PermissionQueryBinder.bind("alpha", emptyIn.leftoverScope());
        assertTrue(emptyBound.whereSql().contains("FALSE"));
        assertTrue(port.verifyChain().intact());
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
