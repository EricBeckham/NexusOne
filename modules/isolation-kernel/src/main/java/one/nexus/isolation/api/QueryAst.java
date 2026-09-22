package one.nexus.isolation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restricted Query AST. Callers cannot supply SQL text or unregistered identifiers.
 * Single-table equality + IS NULL remains the write / default read path. SELECT may
 * add INNER / OUTER / CROSS JOIN of registered tables; SELECT / UPDATE / DELETE
 * may add IN / EXISTS subqueries, including nested subquery-in-subquery; SELECT
 * may add ORDER BY + LIMIT/OFFSET; INSERT may be a multi-row batch. Each tenant
 * table still gets its own {@code tenant_id} predicate.
 */
public final class QueryAst {
    private final QueryKind kind;
    private final String table;
    private final List<String> projection;
    private final Map<String, Object> values;
    private final Map<String, Object> filters;
    private final List<QueryJoin> joins;
    private final List<QuerySubquery> subqueries;
    private final List<QuerySort> sorts;
    private final QueryPage page;
    private final List<Map<String, Object>> batchRows;

    private QueryAst(
            QueryKind kind,
            String table,
            List<String> projection,
            Map<String, Object> values,
            Map<String, Object> filters,
            List<QueryJoin> joins,
            List<QuerySubquery> subqueries,
            List<QuerySort> sorts,
            QueryPage page,
            List<Map<String, Object>> batchRows
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.table = Objects.requireNonNull(table, "table");
        this.projection = List.copyOf(projection);
        this.values = copyAllowingNulls(values);
        this.filters = copyAllowingNulls(filters);
        this.joins = List.copyOf(joins);
        this.subqueries = List.copyOf(subqueries);
        this.sorts = List.copyOf(sorts);
        this.page = page;
        this.batchRows = copyRows(batchRows);
    }

    private static Map<String, Object> copyAllowingNulls(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            copy.add(copyAllowingNulls(row));
        }
        return List.copyOf(copy);
    }

    public static QueryAst select(String table, List<String> projection, Map<String, Object> filters) {
        return select(table, projection, filters, List.of());
    }

    public static QueryAst select(
            String table,
            List<String> projection,
            Map<String, Object> filters,
            List<QueryJoin> joins
    ) {
        return new QueryAst(
                QueryKind.SELECT, table, projection, Map.of(), filters, joins, List.of(), List.of(), null, List.of());
    }

    public static QueryAst insert(String table, Map<String, Object> values) {
        return new QueryAst(
                QueryKind.INSERT, table, List.of(), values, Map.of(), List.of(), List.of(), List.of(), null, List.of());
    }

    public static QueryAst insertBatch(String table, List<Map<String, Object>> rows) {
        return new QueryAst(
                QueryKind.INSERT, table, List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), null, rows);
    }

    public static QueryAst update(String table, Map<String, Object> values, Map<String, Object> filters) {
        return new QueryAst(
                QueryKind.UPDATE, table, List.of(), values, filters, List.of(), List.of(), List.of(), null, List.of());
    }

    public static QueryAst delete(String table, Map<String, Object> filters) {
        return new QueryAst(
                QueryKind.DELETE, table, List.of(), Map.of(), filters, List.of(), List.of(), List.of(), null, List.of());
    }

    public QueryKind kind() {
        return kind;
    }

    public String table() {
        return table;
    }

    public List<String> projection() {
        return projection;
    }

    public Map<String, Object> values() {
        return values;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public List<QueryJoin> joins() {
        return joins;
    }

    public List<QuerySubquery> subqueries() {
        return subqueries;
    }

    public List<QuerySort> sorts() {
        return sorts;
    }

    public QueryPage page() {
        return page;
    }

    public List<Map<String, Object>> batchRows() {
        return batchRows;
    }

    public QueryAst withValues(Map<String, Object> nextValues) {
        return new QueryAst(kind, table, projection, nextValues, filters, joins, subqueries, sorts, page, batchRows);
    }

    public QueryAst withFilters(Map<String, Object> nextFilters) {
        return new QueryAst(kind, table, projection, values, nextFilters, joins, subqueries, sorts, page, batchRows);
    }

    public QueryAst withJoins(List<QueryJoin> nextJoins) {
        return new QueryAst(kind, table, projection, values, filters, nextJoins, subqueries, sorts, page, batchRows);
    }

    public QueryAst withSubqueries(List<QuerySubquery> nextSubqueries) {
        return new QueryAst(kind, table, projection, values, filters, joins, nextSubqueries, sorts, page, batchRows);
    }

    public QueryAst withSorts(List<QuerySort> nextSorts) {
        return new QueryAst(kind, table, projection, values, filters, joins, subqueries, nextSorts, page, batchRows);
    }

    public QueryAst withPage(QueryPage nextPage) {
        return new QueryAst(kind, table, projection, values, filters, joins, subqueries, sorts, nextPage, batchRows);
    }

    public QueryAst withBatchRows(List<Map<String, Object>> nextRows) {
        return new QueryAst(kind, table, projection, values, filters, joins, subqueries, sorts, page, nextRows);
    }
}
