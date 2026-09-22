package one.nexus.isolation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Restricted INNER JOIN. Table and column names must come from {@code TableRegistry}.
 * Callers cannot supply SQL text. Tenant-scoped join tables receive a mandatory
 * {@code tenant_id} predicate at compile time.
 */
public final class QueryJoin {
    private final String table;
    private final String leftColumn;
    private final String rightColumn;
    private final Map<String, Object> filters;

    private QueryJoin(String table, String leftColumn, String rightColumn, Map<String, Object> filters) {
        this.table = Objects.requireNonNull(table, "table");
        this.leftColumn = Objects.requireNonNull(leftColumn, "leftColumn");
        this.rightColumn = Objects.requireNonNull(rightColumn, "rightColumn");
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(
                filters == null ? Map.of() : filters));
    }

    public static QueryJoin inner(String table, String leftColumn, String rightColumn) {
        return inner(table, leftColumn, rightColumn, Map.of());
    }

    public static QueryJoin inner(
            String table, String leftColumn, String rightColumn, Map<String, Object> filters) {
        return new QueryJoin(table, leftColumn, rightColumn, filters);
    }

    public String table() {
        return table;
    }

    public String leftColumn() {
        return leftColumn;
    }

    public String rightColumn() {
        return rightColumn;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public QueryJoin withFilters(Map<String, Object> nextFilters) {
        return new QueryJoin(table, leftColumn, rightColumn, nextFilters);
    }
}
