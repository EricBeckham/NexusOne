package one.nexus.permission.scope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.spi.DataScopeInjector;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;

/**
 * M0-02 {@code DataScopeInjector} seam. Isolation kernel injects {@code tenant_id}
 * first. This implementation may only AND extra equality atoms and must never
 * remove or replace the tenant predicate. Wire with
 * {@code IsolationKernel.create(..., injector, ...)} when extras are known;
 * per-request SELF/DEPT encoding uses {@link QueryAstPolicyCompiler}.
 */
public final class IsolationDataScopeInjector implements DataScopeInjector {
    private final Map<String, Object> extraAnds;

    public IsolationDataScopeInjector() {
        this(Map.of());
    }

    public IsolationDataScopeInjector(Map<String, Object> extraAnds) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (extraAnds != null) {
            extraAnds.forEach(copy::put);
        }
        this.extraAnds = Collections.unmodifiableMap(copy);
    }

    public IsolationDataScopeInjector(Decision decision) {
        this(safeEquals(decision));
    }

    @Override
    public QueryAst inject(QueryAst query, TableDescriptor table, AccessContext context) {
        Objects.requireNonNull(query, "query");
        if (query.kind() == QueryKind.INSERT) {
            return query;
        }
        Map<String, Object> filters = query.filters();
        if (filters.containsKey("tenant_id")) {
            return query.withFilters(inject(filters, extrasFor(table)));
        }
        if (table != null && !table.tenantRequired()) {
            return query;
        }
        if (context != null && context.platform()) {
            return query;
        }
        throw PermissionException.tenantPredicateRequired("DataScopeInjector refused missing tenant_id");
    }

    public Map<String, Object> inject(Map<String, Object> filtersAfterTenant, Object table, Object context) {
        return inject(filtersAfterTenant, extraAnds(context));
    }

    public Map<String, Object> inject(Map<String, Object> filtersAfterTenant, Map<String, Object> extras) {
        if (filtersAfterTenant == null || !filtersAfterTenant.containsKey("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("DataScopeInjector refused missing tenant_id");
        }
        Object tenant = filtersAfterTenant.get("tenant_id");
        if (tenant == null) {
            throw PermissionException.tenantPredicateRequired("DataScopeInjector refused null tenant_id");
        }
        Map<String, Object> next = new LinkedHashMap<>(filtersAfterTenant);
        if (extras != null) {
            extras.forEach((column, value) -> {
                if ("tenant_id".equals(column) && !Objects.equals(tenant, value)) {
                    throw PermissionException.tenantPredicateRequired("DataScopeInjector cannot replace tenant_id");
                }
                if (!"tenant_id".equals(column)) {
                    next.put(column, value);
                }
            });
        }
        if (!Objects.equals(tenant, next.get("tenant_id"))) {
            throw PermissionException.tenantPredicateRequired("DataScopeInjector stripped tenant_id");
        }
        return Map.copyOf(next);
    }

    private Map<String, Object> extrasFor(TableDescriptor table) {
        if (table == null) {
            return extraAnds;
        }
        Map<String, Object> allowed = new LinkedHashMap<>();
        extraAnds.forEach((column, value) -> {
            if (table.columns().contains(column)) {
                allowed.put(column, value);
            }
        });
        return allowed;
    }

    private static Map<String, Object> extraAnds(Object context) {
        if (context instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    out.put(String.valueOf(key), value);
                }
            });
            return out;
        }
        return Map.of();
    }

    private static Map<String, Object> safeEquals(Decision decision) {
        if (decision == null || !decision.allowed()) {
            return Map.of();
        }
        return QueryAstPolicyCompiler.equalityFilters(
                decision.scope(),
                QueryAstPolicyCompiler.ENCODABLE_COLUMNS);
    }
}
