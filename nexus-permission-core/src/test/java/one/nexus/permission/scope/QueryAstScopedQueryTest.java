package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                                Map.of("deleted_at", null))
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
                        Map.of("id", "r1", "deleted_at", null)),
                self,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(detail.compiled().sql().contains("\"id\" = ?"));
        assertTrue(detail.compiled().sql().contains("\"tenant_id\" = ?"));
        assertFalse(detail.compiled().sql().contains("tenant_id = ? AND"));

        BoundSql count = QueryAstScopedQuery.count(QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of("deleted_at", null)),
                self,
                table,
                kernel.compiler(),
                AccessContextHolder.current()).bound());
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains("tenant_id"));
        assertEquals(List.of("u1", "alpha"), count.parameters());
    }

    @Test
    void leftoverOrStaysOnBinderBeforeOrderAndLimit() {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-or"));
        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "u1"),
                Predicate.atom("department_id", "=", "d1"));
        Decision mixed = new Decision(true, "or", orScope, Map.of(), 1L);
        QueryAstScopedQuery.CompiledRead leftover = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                mixed,
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertFalse(leftover.scopeFullyEncoded());
        assertEquals(orScope, leftover.encoding().leftoverScope());
        assertFalse(leftover.compiled().sql().contains("owner_id"));
        String bound = leftover.bound().sql();
        int order = bound.toLowerCase().indexOf(" order by ");
        int leftoverAt = bound.indexOf("AND (");
        assertTrue(leftoverAt > 0 && leftoverAt < order);
        assertTrue(bound.contains("owner_id = ?"));
        assertTrue(bound.contains("department_id = ?"));
        assertTrue(bound.startsWith(leftover.compiled().sql().substring(0, leftover.compiled().sql().indexOf(" ORDER"))));
        assertEquals(List.of("alpha", "u1", "d1", 10, 0), leftover.bound().parameters());

        BoundSql count = QueryAstScopedQuery.count(QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of("deleted_at", null)),
                mixed,
                table,
                kernel.compiler(),
                AccessContextHolder.current()).bound());
        assertTrue(count.sql().contains("tenant_id"));
        assertTrue(count.sql().contains("owner_id"));

        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                new Decision(true, "bad", Predicate.atom("tenant_id", "=", "beta"), Map.of(), 1L),
                table));
    }

    private static IsolationKernel kernel() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id", "department_id", "deleted_at", "secret");
        return IsolationKernel.create(registry, Set.of("ops.summary"), new CollectingAuditSink());
    }
}
