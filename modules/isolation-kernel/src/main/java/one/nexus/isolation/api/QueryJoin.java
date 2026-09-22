package one.nexus.isolation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restricted JOIN. INNER / LEFT / RIGHT / FULL OUTER / CROSS are first-class
 * shapes. {@code TenantInjector} adds {@code tenant_id} per joined tenant table.
 * CROSS JOIN cannot carry {@code ON}; OUTER / INNER require equality {@code ON}.
 */
public final class QueryJoin {
    public enum Kind {
        INNER("INNER JOIN", true),
        LEFT("LEFT OUTER JOIN", true),
        RIGHT("RIGHT OUTER JOIN", true),
        FULL("FULL OUTER JOIN", true),
        CROSS("CROSS JOIN", false);

        private final String sql;
        private final boolean requiresOn;

        Kind(String sql, boolean requiresOn) {
            this.sql = sql;
            this.requiresOn = requiresOn;
        }

        public String sql() {
            return sql;
        }

        public boolean requiresOn() {
            return requiresOn;
        }
    }

    private final Kind kind;
    private final String table;
    private final String alias;
    private final List<JoinOn> on;
    private final Map<String, Object> filters;

    private QueryJoin(Kind kind, String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.table = Objects.requireNonNull(table, "table");
        this.alias = Objects.requireNonNull(alias, "alias");
        this.on = List.copyOf(on);
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(filters));
    }

    public static QueryJoin of(Kind kind, String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return new QueryJoin(kind, table, alias, on, filters);
    }

    public static QueryJoin inner(String table, String alias, List<JoinOn> on) {
        return inner(table, alias, on, Map.of());
    }

    public static QueryJoin inner(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return of(Kind.INNER, table, alias, on, filters);
    }

    public static QueryJoin left(String table, String alias, List<JoinOn> on) {
        return left(table, alias, on, Map.of());
    }

    public static QueryJoin left(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return of(Kind.LEFT, table, alias, on, filters);
    }

    public static QueryJoin right(String table, String alias, List<JoinOn> on) {
        return right(table, alias, on, Map.of());
    }

    public static QueryJoin right(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return of(Kind.RIGHT, table, alias, on, filters);
    }

    public static QueryJoin full(String table, String alias, List<JoinOn> on) {
        return full(table, alias, on, Map.of());
    }

    public static QueryJoin full(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return of(Kind.FULL, table, alias, on, filters);
    }

    public static QueryJoin cross(String table, String alias) {
        return cross(table, alias, Map.of());
    }

    public static QueryJoin cross(String table, String alias, Map<String, Object> filters) {
        return of(Kind.CROSS, table, alias, List.of(), filters);
    }

    public Kind kind() {
        return kind;
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public List<JoinOn> on() {
        return on;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public QueryJoin withFilters(Map<String, Object> nextFilters) {
        return new QueryJoin(kind, table, alias, on, nextFilters);
    }
}
