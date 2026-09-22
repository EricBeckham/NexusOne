package one.nexus.isolation.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.TableDescriptor;

/**
 * M0-05 DataScope seam. Isolation kernel always injects tenant_id first; DataScope may only AND extra predicates.
 * Implementations must not remove or replace the tenant predicate. Safe SELF/DEPT
 * equality atoms may be AND-ed via {@link #andEquals(Map)}; OR / IN trees stay
 * outside this AST.
 */
@FunctionalInterface
public interface DataScopeInjector {
    QueryAst inject(QueryAst query, TableDescriptor table, AccessContext context);

    static DataScopeInjector passthrough() {
        return (query, table, context) -> query;
    }

    /**
     * AND registered equality atoms onto SELECT / UPDATE / DELETE filters.
     * Never authors {@code tenant_id}. Unknown columns are ignored.
     */
    static DataScopeInjector andEquals(Map<String, Object> extras) {
        Map<String, Object> atoms = extras == null ? Map.of() : Map.copyOf(extras);
        return (query, table, context) -> {
            if (query.kind() == QueryKind.INSERT || atoms.isEmpty()) {
                return query;
            }
            Map<String, Object> next = new LinkedHashMap<>(query.filters());
            atoms.forEach((column, value) -> {
                if ("tenant_id".equals(column)) {
                    return;
                }
                if (table != null && !table.columns().contains(column)) {
                    return;
                }
                next.put(column, value);
            });
            return query.withFilters(next);
        };
    }
}
