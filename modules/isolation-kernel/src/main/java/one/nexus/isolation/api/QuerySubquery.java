package one.nexus.isolation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restricted IN / EXISTS subquery. TenantInjector adds {@code tenant_id} on
 * every tenant table in the tree, including nested subquery-in-subquery.
 * Nesting deeper than {@link #MAX_NESTING} is rejected before render.
 */
public final class QuerySubquery {
    public enum Kind {
        IN,
        EXISTS
    }

    /** Inclusive cap on subquery nesting (top-level subquery is depth 1). */
    public static final int MAX_NESTING = 8;

    private final Kind kind;
    private final String outerColumn;
    private final String table;
    private final String alias;
    private final String projection;
    private final List<JoinOn> correlated;
    private final Map<String, Object> filters;
    private final List<QuerySubquery> nested;

    private QuerySubquery(
            Kind kind,
            String outerColumn,
            String table,
            String alias,
            String projection,
            List<JoinOn> correlated,
            Map<String, Object> filters,
            List<QuerySubquery> nested
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.outerColumn = Objects.requireNonNull(outerColumn, "outerColumn");
        this.table = Objects.requireNonNull(table, "table");
        this.alias = Objects.requireNonNull(alias, "alias");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.correlated = List.copyOf(correlated);
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        this.nested = List.copyOf(nested);
    }

    public static QuerySubquery in(String outerColumn, String table, String projection, Map<String, Object> filters) {
        return in(outerColumn, table, table, projection, filters);
    }

    public static QuerySubquery in(
            String outerColumn,
            String table,
            String alias,
            String projection,
            Map<String, Object> filters
    ) {
        return new QuerySubquery(Kind.IN, outerColumn, table, alias, projection, List.of(), filters, List.of());
    }

    public static QuerySubquery exists(String table, String alias, List<JoinOn> correlated, Map<String, Object> filters) {
        return new QuerySubquery(Kind.EXISTS, "", table, alias, "id", correlated, filters, List.of());
    }

    public Kind kind() {
        return kind;
    }

    public String outerColumn() {
        return outerColumn;
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public String projection() {
        return projection;
    }

    public List<JoinOn> correlated() {
        return correlated;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public List<QuerySubquery> nested() {
        return nested;
    }

    public QuerySubquery withFilters(Map<String, Object> nextFilters) {
        return new QuerySubquery(kind, outerColumn, table, alias, projection, correlated, nextFilters, nested);
    }

    public QuerySubquery withNested(List<QuerySubquery> nextNested) {
        return new QuerySubquery(kind, outerColumn, table, alias, projection, correlated, filters, nextNested);
    }
}
