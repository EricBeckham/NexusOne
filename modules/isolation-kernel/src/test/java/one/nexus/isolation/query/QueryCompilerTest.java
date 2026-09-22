package one.nexus.isolation.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.JoinOn;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.QuerySubquery;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.spi.DataScopeInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCompilerTest {
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private IsolationKernel kernel(CollectingAuditSink sink) {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "deleted_at");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
        registry.registerPlatform("platform_metric", "p1", 1, "id", "count");
        registry.freeze();
        return IsolationKernel.create(registry, Set.of("ops.summary"), sink);
    }

    private TenantContext alpha(IsolationKernel kernel) {
        return kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")), "trace-a");
    }

    @Test
    void injectsTenantPredicateAndRendersParameters() {
        IsolationKernel kernel = kernel(new CollectingAuditSink());
        TenantContext alpha = alpha(kernel);
        RenderedSql read = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id", "title"), map("deleted_at", null)), alpha);
        assertEquals("SELECT \"id\", \"title\" FROM \"ledger_entry\" WHERE \"deleted_at\" IS NULL AND \"tenant_id\" = ?", read.sql());
        assertEquals(List.of("alpha"), read.parameters());
        assertEquals("TenantContext", read.plan().tenantSource());
        assertTrue(read.plan().tenantRequired());

        RenderedSql inserted = kernel.compiler().compile(
                QueryAst.insert("ledger_entry", map("id", "1", "title", "hi")), alpha);
        assertEquals("INSERT INTO \"ledger_entry\" (\"id\", \"title\", \"tenant_id\") VALUES (?, ?, ?)", inserted.sql());
        assertEquals(List.of("1", "hi", "alpha"), inserted.parameters());

        RenderedSql nullable = kernel.compiler().compile(
                QueryAst.insert("ledger_entry", map("id", "2", "title", null)), alpha);
        assertEquals("INSERT INTO \"ledger_entry\" (\"id\", \"title\", \"tenant_id\") VALUES (?, ?, ?)", nullable.sql());
        assertEquals(3, nullable.parameters().size());
        assertEquals("2", nullable.parameters().get(0));
        assertEquals(null, nullable.parameters().get(1));
        assertEquals("alpha", nullable.parameters().get(2));
    }

    @Test
    void rejectsCrossTenantAndMissingContextBeforeSql() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        assertCode(IsolationErrorCode.MISSING_CONTEXT, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()), null));
        assertCode(IsolationErrorCode.CROSS_TENANT_WRITE, () -> kernel.compiler().compile(
                QueryAst.insert("ledger_entry", map("id", "1", "tenant_id", "beta")), alpha));
        assertCode(IsolationErrorCode.CROSS_TENANT_WRITE, () -> kernel.compiler().compile(
                QueryAst.update("ledger_entry", map("title", "x"), map("id", "1", "tenant_id", "beta")), alpha));
        assertCode(IsolationErrorCode.TENANT_ID_IMMUTABLE, () -> kernel.compiler().compile(
                QueryAst.update("ledger_entry", map("tenant_id", "beta"), map("id", "1")), alpha));
        assertCode(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, () -> kernel.compiler().compile(
                QueryAst.select("platform_metric", List.of("id"), Map.of()), alpha));
        assertCode(IsolationErrorCode.UNREGISTERED_TABLE, () -> kernel.compiler().compile(
                QueryAst.select("unknown_table", List.of("id"), Map.of()), alpha));
        assertCode(IsolationErrorCode.UNREGISTERED_COLUMN, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("bad_column"), Map.of()), alpha));
        assertCode(IsolationErrorCode.INVALID_IDENTIFIER, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry;drop table ledger_entry", List.of("id"), Map.of()), alpha));
        assertTrue(sink.rejectCount() >= 5);
    }

    @Test
    void platformContextIsAuditedAndCanReadAcrossTenants() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        PlatformContext platform = kernel.platform().issue(
                "ops.summary", Actor.platformAdmin("admin"), "operations report", "t-1");
        RenderedSql summary = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()), platform);
        assertEquals("SELECT \"id\" FROM \"ledger_entry\"", summary.sql());
        assertTrue(summary.plan().platformContext());
        assertEquals("SELECT \"id\" FROM \"platform_metric\"",
                kernel.compiler().compile(QueryAst.select("platform_metric", List.of("id"), Map.of()), platform).sql());
        NativeSqlPort nativePort = kernel.nativeSql(Map.of("infra.count-ledger", "ledger_entry"));
        RenderedSql nativeCount = nativePort.compileCount("infra.count-ledger", alpha(kernel));
        assertEquals("SELECT count(*) FROM \"ledger_entry\" WHERE \"tenant_id\" = ?", nativeCount.sql());
        assertCode(IsolationErrorCode.NATIVE_TEMPLATE_NOT_WHITELISTED,
                () -> nativePort.compileCount("business.raw-sql", alpha(kernel)));
    }

    @Test
    void dataScopeCannotStripTenantPredicate() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        DataScopeInjector hostile = (query, table, context) -> query.withFilters(Map.of("id", "1"));
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), hostile, (q, t, c) -> q);
        TenantContext alpha = alpha(kernel);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(QueryAst.select("ledger_entry", List.of("id"), Map.of()), alpha));
        assertEquals(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, ex.error());
    }

    @Test
    void innerJoinInjectsTenantOnBothTables() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        RenderedSql joined = kernel.compiler().compile(joinSelect(List.of("ledger_entry.id", "line.amount"), Map.of()), alpha);
        assertEquals(
                "SELECT \"ledger_entry\".\"id\", \"line\".\"amount\" FROM \"ledger_entry\" \"ledger_entry\""
                        + " INNER JOIN \"ledger_line\" \"line\""
                        + " ON \"ledger_entry\".\"id\" = \"line\".\"entry_id\" AND \"line\".\"tenant_id\" = ?"
                        + " WHERE \"ledger_entry\".\"tenant_id\" = ?",
                joined.sql());
        assertEquals(List.of("alpha", "alpha"), joined.parameters());
        assertEquals(0, sink.rejectCount());
    }

    @Test
    void crossTenantJoinIsRejectedBeforeRender() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(joinSelect(List.of("id"), map("tenant_id", "beta")), alpha));
        assertEquals(IsolationErrorCode.CROSS_TENANT_JOIN, ex.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_JOIN));
        assertCode(IsolationErrorCode.CROSS_TENANT_JOIN, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of(), List.of(
                        QueryJoin.inner("ledger_line", "line", List.of(
                                new JoinOn("ledger_entry", "id", "line", "entry_id")),
                                map("tenant_id", null)))),
                alpha));
        assertCode(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of(), List.of(
                        QueryJoin.inner("platform_metric", "metric", List.of(
                                new JoinOn("ledger_entry", "id", "metric", "id"))))),
                alpha));
        assertTrue(sink.rejectCount() >= 3);
    }

    @Test
    void dataScopeCannotStripJoinTenantPredicate() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
        DataScopeInjector hostile = (query, table, context) -> query.withJoins(query.joins().stream()
                .map(join -> join.withFilters(Map.of("entry_id", "1")))
                .toList());
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), hostile, (q, t, c) -> q);
        TenantContext alpha = alpha(kernel);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(joinSelect(List.of("id"), Map.of()), alpha));
        assertEquals(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, ex.error());
    }

    @Test
    void subquerySortAndPageInjectTenantAndRenderParameters() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        RenderedSql inSelect = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id", "title"), Map.of())
                        .withSubqueries(List.of(QuerySubquery.in("id", "ledger_line", "entry_id", Map.of())))
                        .withSorts(List.of(QuerySort.asc("title"), QuerySort.desc("id")))
                        .withPage(QueryPage.of(20, 0)),
                alpha);
        assertEquals(
                "SELECT \"id\", \"title\" FROM \"ledger_entry\""
                        + " WHERE \"tenant_id\" = ?"
                        + " AND \"id\" IN (SELECT \"entry_id\" FROM \"ledger_line\" WHERE \"tenant_id\" = ?)"
                        + " ORDER BY \"title\" ASC, \"id\" DESC"
                        + " LIMIT ? OFFSET ?",
                inSelect.sql());
        assertEquals(List.of("alpha", "alpha", 20, 0), inSelect.parameters());

        RenderedSql exists = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSubqueries(List.of(QuerySubquery.exists(
                                "ledger_line",
                                "line",
                                List.of(new JoinOn("ledger_entry", "id", "line", "entry_id")),
                                Map.of()))),
                alpha);
        assertEquals(
                "SELECT \"id\" FROM \"ledger_entry\""
                        + " WHERE \"tenant_id\" = ?"
                        + " AND EXISTS (SELECT 1 FROM \"ledger_line\" \"line\""
                        + " WHERE \"ledger_entry\".\"id\" = \"line\".\"entry_id\" AND \"line\".\"tenant_id\" = ?)",
                exists.sql());
        assertEquals(List.of("alpha", "alpha"), exists.parameters());
        assertEquals(0, sink.rejectCount());
    }

    @Test
    void crossTenantSubqueryIsRejectedBeforeRender() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        IsolationException stolen = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(
                        QueryAst.select("ledger_entry", List.of("id"), Map.of())
                                .withSubqueries(List.of(
                                        QuerySubquery.in("id", "ledger_line", "entry_id", map("tenant_id", "beta")))),
                        alpha));
        assertEquals(IsolationErrorCode.CROSS_TENANT_SUBQUERY, stolen.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_SUBQUERY));
        assertCode(IsolationErrorCode.CROSS_TENANT_SUBQUERY, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSubqueries(List.of(
                                QuerySubquery.in("id", "ledger_line", "entry_id", map("tenant_id", null)))),
                alpha));
        assertCode(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSubqueries(List.of(
                                QuerySubquery.in("id", "platform_metric", "id", Map.of()))),
                alpha));
        assertTrue(sink.rejectCount() >= 3);
    }

    @Test
    void dataScopeCannotStripSubqueryTenantPredicate() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        registry.registerTenant("ledger_line", "p3", 1, "id", "tenant_id", "entry_id", "amount");
        DataScopeInjector hostile = (query, table, context) -> query.withSubqueries(query.subqueries().stream()
                .map(sub -> sub.withFilters(Map.of("entry_id", "1")))
                .toList());
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), hostile, (q, t, c) -> q);
        TenantContext alpha = alpha(kernel);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(
                        QueryAst.select("ledger_entry", List.of("id"), Map.of())
                                .withSubqueries(List.of(QuerySubquery.in("id", "ledger_line", "entry_id", Map.of()))),
                        alpha));
        assertEquals(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, ex.error());
    }

    @Test
    void insertBatchInjectsTenantOnEveryRow() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        RenderedSql batch = kernel.compiler().compile(
                QueryAst.insertBatch("ledger_entry", List.of(
                        map("id", "1", "title", "a"),
                        map("id", "2", "title", "b"))),
                alpha);
        assertEquals(
                "INSERT INTO \"ledger_entry\" (\"id\", \"title\", \"tenant_id\") VALUES (?, ?, ?), (?, ?, ?)",
                batch.sql());
        assertEquals(List.of("1", "a", "alpha", "2", "b", "alpha"), batch.parameters());
        assertEquals(0, sink.rejectCount());
    }

    @Test
    void crossTenantBatchIsRejectedBeforeRender() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        IsolationException stolen = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(
                        QueryAst.insertBatch("ledger_entry", List.of(
                                map("id", "1", "title", "a"),
                                map("id", "2", "title", "b", "tenant_id", "beta"))),
                        alpha));
        assertEquals(IsolationErrorCode.CROSS_TENANT_BATCH, stolen.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_BATCH));
        assertCode(IsolationErrorCode.CROSS_TENANT_BATCH, () -> kernel.compiler().compile(
                QueryAst.insertBatch("ledger_entry", List.of(map("id", "1", "title", "a", "tenant_id", null))),
                alpha));
        assertTrue(sink.rejectCount() >= 2);
    }

    @Test
    void dataScopeCannotChangeBatchTenant() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
        DataScopeInjector hostile = (query, table, context) -> query.withBatchRows(query.batchRows().stream()
                .map(row -> map("id", row.get("id"), "title", row.get("title"), "tenant_id", "beta"))
                .toList());
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), hostile, (q, t, c) -> q);
        TenantContext alpha = alpha(kernel);
        IsolationException ex = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(
                        QueryAst.insertBatch("ledger_entry", List.of(map("id", "1", "title", "a"))),
                        alpha));
        assertEquals(IsolationErrorCode.CROSS_TENANT_BATCH, ex.error());
    }

    @Test
    void invalidSortAndUnboundedPageAreRejectedBeforeRender() {
        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = kernel(sink);
        TenantContext alpha = alpha(kernel);
        IsolationException sort = assertThrows(IsolationException.class, () ->
                kernel.compiler().compile(
                        QueryAst.select("ledger_entry", List.of("id"), Map.of())
                                .withSorts(List.of(QuerySort.asc("not_a_column"))),
                        alpha));
        assertEquals(IsolationErrorCode.INVALID_SORT, sort.error());
        assertTrue(sink.rejected(IsolationErrorCode.INVALID_SORT));
        assertCode(IsolationErrorCode.INVALID_SORT, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withSorts(List.of(QuerySort.desc("id;drop table ledger_entry"))),
                alpha));
        assertCode(IsolationErrorCode.UNBOUNDED_PAGE, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withPage(QueryPage.of(0, 0)),
                alpha));
        assertCode(IsolationErrorCode.UNBOUNDED_PAGE, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withPage(QueryPage.of(QueryPage.MAX_LIMIT + 1, 0)),
                alpha));
        assertCode(IsolationErrorCode.UNBOUNDED_PAGE, () -> kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of())
                        .withPage(QueryPage.of(10, -1)),
                alpha));
        assertTrue(sink.rejectCount() >= 5);
    }

    private static QueryAst joinSelect(List<String> projection, Map<String, Object> joinFilters) {
        return QueryAst.select("ledger_entry", projection, Map.of(), List.of(
                QueryJoin.inner("ledger_line", "line", List.of(
                        new JoinOn("ledger_entry", "id", "line", "entry_id")), joinFilters)));
    }

    private static void assertCode(IsolationErrorCode code, Runnable action) {
        IsolationException ex = assertThrows(IsolationException.class, action::run);
        assertEquals(code, ex.error());
    }
}
