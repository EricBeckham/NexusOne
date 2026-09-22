package one.nexus.permission.scope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import one.nexus.permission.support.PermissionException;

/**
 * M0-02 {@code DataScopeInjector} seam. Isolation kernel injects {@code tenant_id}
 * first. This implementation may only AND extra equality atoms and must never
 * remove or replace the tenant predicate. Wire with
 * {@code IsolationKernel.create(..., this::inject, ...)} when the kernel is on
 * the classpath — the method signature matches the SPI.
 */
public final class IsolationDataScopeInjector {
    public Map<String, Object> inject(Map<String, Object> filtersAfterTenant, Object table, Object context) {
        return inject(filtersAfterTenant, extraAnds(context));
    }

    public Map<String, Object> inject(Map<String, Object> filtersAfterTenant, Map<String, Object> extraAnds) {
        if (filtersAfterTenant == null || !filtersAfterTenant.containsKey("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("DataScopeInjector refused missing tenant_id");
        }
        Object tenant = filtersAfterTenant.get("tenant_id");
        if (tenant == null) {
            throw PermissionException.tenantPredicateRequired("DataScopeInjector refused null tenant_id");
        }
        Map<String, Object> next = new LinkedHashMap<>(filtersAfterTenant);
        if (extraAnds != null) {
            extraAnds.forEach((column, value) -> {
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
}
