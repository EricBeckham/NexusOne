package one.nexus.isolation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Restricted INNER JOIN. TenantInjector adds {@code tenant_id} per joined tenant table. */
public final class QueryJoin {
    private final String table;
    private final String alias;
    private final List<JoinOn> on;
    private final Map<String, Object> filters;

    private QueryJoin(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        this.table = Objects.requireNonNull(table, "table");
        this.alias = Objects.requireNonNull(alias, "alias");
        this.on = List.copyOf(on);
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(filters));
    }

    public static QueryJoin inner(String table, String alias, List<JoinOn> on) {
        return inner(table, alias, on, Map.of());
    }

    public static QueryJoin inner(String table, String alias, List<JoinOn> on, Map<String, Object> filters) {
        return new QueryJoin(table, alias, on, filters);
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
        return new QueryJoin(table, alias, on, nextFilters);
    }
}
