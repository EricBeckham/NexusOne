package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueryAstScopedQueryTest {
    private final TableDescriptor table = new TableDescriptor(
            "ledger_entry",
            TableScope.TENANT,
            Set.of("id", "tenant_id", "title", "owner_id", "department_id", "deleted_at", "secret"),
            "p3",
            1);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void compilesSelfListAndDetailThroughQueryCompiler() {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-optin"));
        Decision self = new Decision(
                true,
                "self",
                Predicate.atom("owner_id", "=", "u1"),
                Map.of(
                        "title", new FieldRule(true, true),
                        "secret", new FieldRule(false, false)),
                1L);
        QueryAstScopedQuery.CompiledRead list = QueryAstScopedQuery.compile(
                QueryAst.select(
                                "ledger_entry",
                                List.of("id", "title", "secret"),
                                filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(20, 0)),
                self,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(list.scopeFullyEncoded());
        assertEquals(List.of("id", "title"), list.encoding().query().projection());
        assertFalse(list.compiled().sql().contains("secret"));
        assertTrue(list.compiled().sql().contains("\"owner_id\" = ?"));
        assertTrue(list.compiled().sql().contains("\"tenant_id\" = ?"));
        assertTrue(list.compiled().sql().contains("\"deleted_at\" IS NULL"));
        assertTrue(list.compiled().sql().contains("LIMIT ? OFFSET ?"));
        assertEquals(list.compiled().sql(), list.bound().sql());

        QueryAstScopedQuery.CompiledRead detail = QueryAstScopedQuery.compile(
                QueryAst.select(
                        "ledger_entry",
                        List.of("id", "title"),
                        filters("id", "r1", "deleted_at", null)),
                self,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(detail.compiled().sql().contains("\"id\" = ?"));
        assertTrue(detail.compiled().sql().contains("\"tenant_id\" = ?"));
        assertFalse(detail.compiled().sql().contains("tenant_id = ? AND"));

        BoundSql count = QueryAstScopedQuery.count(QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null)),
                self,
                table,
                kernel.compiler(),
                AccessContextHolder.current()).bound());
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains("tenant_id"));
        assertEquals(List.of("u1", "alpha"), count.parameters());
    }

    @Test
    void mixedColumnOrCompilesThroughQueryCompilerBeforeOrderAndLimit() {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-or"));
        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "u1"),
                Predicate.atom("department_id", "=", "d1"));
        Decision mixed = new Decision(true, "or", orScope, Map.of(), 1L);
        QueryAstScopedQuery.CompiledRead encoded = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                mixed,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(encoded.scopeFullyEncoded());
        assertEquals(Predicate.TRUE, encoded.encoding().leftoverScope());
        String sql = encoded.compiled().sql();
        assertTrue(sql.contains("(\"owner_id\" = ? OR \"department_id\" = ?)"));
        assertTrue(sql.contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(sql.indexOf("(\"owner_id\" = ? OR \"department_id\" = ?)")
                < sql.toLowerCase().indexOf(" order by "));
        assertEquals(sql, encoded.bound().sql());
        assertEquals(List.of("alpha", "u1", "d1", 10, 0), encoded.bound().parameters());

        BoundSql count = QueryAstScopedQuery.count(encoded);
        assertTrue(count.sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(count.sql().contains("(\"owner_id\" = ? OR \"department_id\" = ?)"));
        assertEquals(encoded.compiled().plan(), encoded.executed().plan());

        Predicate leftoverScope = Predicate.anyOf(
                Predicate.atom("title", "=", "x"),
                Predicate.atom("secret", "=", "s"));
        QueryAstScopedQuery.CompiledRead leftover = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                new Decision(true, "leftover", leftoverScope, Map.of(), 1L),
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertFalse(leftover.scopeFullyEncoded());
        String bound = leftover.bound().sql();
        int order = bound.toLowerCase().indexOf(" order by ");
        int leftoverAt = bound.indexOf("AND (");
        assertTrue(leftoverAt > 0 && leftoverAt < order);
        assertTrue(bound.contains("title = ?"));

        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                new Decision(true, "bad", Predicate.atom("tenant_id", "=", "beta"), Map.of(), 1L),
                table));
    }

    @Test
    void rawBoundSqlCannotBypassCompilerTenantOnCount() {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-bypass"));
        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT id FROM ledger_entry WHERE tenant_id = ?", List.of("alpha"))));
        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT id FROM ledger_entry", List.of())));
        QueryAstScopedQuery.CompiledRead encoded = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null)),
                new Decision(true, "specified", Predicate.atom("department_id", "IN", List.of("d1", "d2")), Map.of(), 1L),
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(encoded.scopeFullyEncoded());
        var count = QueryAstScopedQuery.countExecuted(encoded);
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains("\"department_id\" IN (?, ?)"));
        assertTrue(count.sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertEquals(encoded.compiled().plan(), count.plan());
    }

    @Test
    void compilesDeptAndChildrenInThroughQueryCompiler() {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-in"));
        Decision specified = new Decision(
                true,
                "specified",
                Predicate.atom("department_id", "IN", List.of("d1", "d2")),
                Map.of(),
                1L);
        QueryAstScopedQuery.CompiledRead compiled = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                specified,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(compiled.scopeFullyEncoded());
        assertEquals(List.of("d1", "d2"), compiled.encoding().query().filters().get("department_id"));
        assertTrue(compiled.compiled().sql().contains("\"department_id\" IN (?, ?)"));
        assertTrue(compiled.compiled().sql().contains("\"tenant_id\" = ?"));
        assertEquals(compiled.compiled().sql(), compiled.bound().sql());
        assertEquals(List.of("d1", "d2", "alpha", 10, 0), compiled.bound().parameters());
    }

    private static Map<String, Object> filters(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static IsolationKernel kernel() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id", "department_id", "deleted_at", "secret");
        return IsolationKernel.create(registry, Set.of("ops.summary"), new CollectingAuditSink());
    }
}
