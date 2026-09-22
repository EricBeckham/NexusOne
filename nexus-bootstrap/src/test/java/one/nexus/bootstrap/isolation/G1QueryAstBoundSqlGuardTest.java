package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.BoundSql;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.QueryAstScopedQuery;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: leftover mixed-column OR and COUNT on GuardedLedgerStore must
 * stay on QueryAstScopedQuery → GuardedJdbc. Raw BoundSql cannot bypass
 * Query AST / DataScope the way unquoted tenant_id could. Does not redo #142
 * IN encoding. Does not invent a second tenant context or change same-TX
 * {@code AuditPort.append} joining.
 */
class G1QueryAstBoundSqlGuardTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryAstBoundSqlGuardTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "l_alpha_ticket",
                "p3",
                1,
                "id",
                "tenant_id",
                "title",
                "owner_id",
                "department_id",
                "deleted_at",
                "phone",
                "secret");
        IsolationAuditSink audit = IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {}));
        this.kernel = IsolationKernel.create(registry, Set.of("ops.summary"), audit);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void leftoverOrAndCountStayOnCompilerSqlAndRejectRawBoundSql() throws Exception {
        bind("alpha");
        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "user-alpha"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstScopedQuery.CompiledRead leftover = QueryAstScopedQuery.compile(
                QueryAst.select("l_alpha_ticket", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                new Decision(true, "or", orScope, Map.of(), 1L),
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertFalse(leftover.scopeFullyEncoded());
        assertTrue(leftover.executed().sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(leftover.executed().sql().contains("owner_id = ?"));
        assertTrue(leftover.executed().sql().indexOf("AND (") < leftover.executed().sql().toLowerCase().indexOf(" order by "));
        assertEquals(leftover.compiled().plan(), leftover.executed().plan());
        var binder = PermissionQueryBinder.bind("alpha", leftover.encoding().leftoverScope());
        assertTrue(binder.whereSql().startsWith("tenant_id = ? AND "));

        var count = QueryAstScopedQuery.countExecuted(leftover);
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(count.sql().contains("owner_id = ?"));
        assertEquals(leftover.compiled().plan(), count.plan());

        QueryAstScopedQuery.CompiledRead specified = QueryAstScopedQuery.compile(
                QueryAst.select("l_alpha_ticket", List.of("id"), filters("deleted_at", null)),
                new Decision(true, "specified", Predicate.atom("department_id", "IN", List.of("d1", "d2")), Map.of(), 1L),
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(specified.scopeFullyEncoded());
        assertTrue(QueryAstScopedQuery.countExecuted(specified).sql().contains("\"department_id\" IN (?, ?)"));

        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT id FROM l_alpha_ticket WHERE tenant_id = ?", List.of("alpha"))));
        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT count(*) FROM l_alpha_ticket", List.of())));

        Map<String, FieldRule> fields = Map.of(
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true));
        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("title", "Alpha", "phone", "13812345678"),
                fields,
                false);
        assertEquals("***", projected.get("phone"));

        String store = Files.readString(storePath());
        assertFalse(store.contains("BoundSql"));
        assertFalse(store.contains("prepareStatement"));
        assertTrue(store.contains("QueryAstScopedQuery.countExecuted"));
        assertTrue(store.contains("QueryAstScopedQuery.executed"));
        assertTrue(port.verifyChain().intact());
        assertTrue(port.findByTraceId("trace-alpha").stream()
                .noneMatch(event -> event.event().category() == AuditCategory.GUARD_REJECT));
    }

    private static Path storePath() {
        Path module = Path.of("nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/GuardedLedgerStore.java");
        if (Files.isRegularFile(module)) {
            return module;
        }
        return Path.of("../nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/GuardedLedgerStore.java");
    }

    private static Map<String, Object> filters(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
