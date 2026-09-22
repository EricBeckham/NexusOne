package one.nexus.isolation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restricted Query AST. Callers cannot supply SQL text or unregistered identifiers.
 * Single-table equality + IS NULL remains the write / default read path. SELECT may
 * add INNER JOIN of registered tables; each tenant table still gets its own
 * {@code tenant_id} predicate. Subquery / batch / sort / page stay out of this type.
 */
public final class QueryAst {
    private final QueryKind kind;
    private final String table;
    private final List<String> projection;
    private final Map<String, Object> values;
    private final Map<String, Object> filters;
    private final List<QueryJoin> joins;

    private QueryAst(
            QueryKind kind,
            String table,
            List<String> projection,
            Map<String, Object> values,
            Map<String, Object> filters,
            List<QueryJoin> joins
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.table = Objects.requireNonNull(table, "table");
        this.projection = List.copyOf(projection);
        this.values = copyAllowingNulls(values);
        this.filters = copyAllowingNulls(filters);
        this.joins = List.copyOf(joins);
    }

    private static Map<String, Object> copyAllowingNulls(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
        return new QueryAst(QueryKind.SELECT, table, projection, Map.of(), filters, joins);
    }

    public static QueryAst insert(String table, Map<String, Object> values) {
        return new QueryAst(QueryKind.INSERT, table, List.of(), values, Map.of(), List.of());
    }

    public static QueryAst update(String table, Map<String, Object> values, Map<String, Object> filters) {
        return new QueryAst(QueryKind.UPDATE, table, List.of(), values, filters, List.of());
    }

    public static QueryAst delete(String table, Map<String, Object> filters) {
        return new QueryAst(QueryKind.DELETE, table, List.of(), Map.of(), filters, List.of());
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

    public QueryAst withValues(Map<String, Object> nextValues) {
        return new QueryAst(kind, table, projection, nextValues, filters, joins);
    }

    public QueryAst withFilters(Map<String, Object> nextFilters) {
        return new QueryAst(kind, table, projection, values, nextFilters, joins);
    }

    public QueryAst withJoins(List<QueryJoin> nextJoins) {
        return new QueryAst(kind, table, projection, values, filters, nextJoins);
    }
}
