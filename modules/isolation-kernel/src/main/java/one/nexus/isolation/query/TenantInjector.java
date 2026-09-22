package one.nexus.isolation.query;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditor;

/**
 * Mandatory tenant injector. There is no disable flag; platform context is the only audited bypass of the predicate.
 */
public final class TenantInjector {
    private TenantInjector() {}

    public static QueryAst inject(QueryAst query, TableDescriptor table, AccessContext context, IsolationAuditor auditor) {
        if (context == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, null, "missing context");
        }
        if (!table.tenantRequired() || context instanceof PlatformContext) {
            return query;
        }
        if (!(context instanceof TenantContext tenant)) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, context, "tenant context required");
        }
        String tenantId = tenant.tenantId();
        if (query.kind() == QueryKind.INSERT) {
            Object provided = query.values().get("tenant_id");
            if (provided != null && !tenantId.equals(provided)) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_WRITE, context, "insert tenant_id mismatch");
            }
            Map<String, Object> values = new LinkedHashMap<>(query.values());
            values.put("tenant_id", tenantId);
            return query.withValues(values);
        }
        Object filtered = query.filters().get("tenant_id");
        if (filtered != null && !tenantId.equals(filtered)) {
            IsolationErrorCode code = query.kind() == QueryKind.SELECT
                    ? IsolationErrorCode.CROSS_TENANT_READ
                    : IsolationErrorCode.CROSS_TENANT_WRITE;
            throw auditor.reject(code, context, "filter tenant_id mismatch");
        }
        Map<String, Object> filters = new LinkedHashMap<>(query.filters());
        filters.put("tenant_id", tenantId);
        return query.withFilters(filters);
    }
}
