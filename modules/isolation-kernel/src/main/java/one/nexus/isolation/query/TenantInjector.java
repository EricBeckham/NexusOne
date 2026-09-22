package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.QuerySubquery;
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
            if (!query.batchRows().isEmpty()) {
                return injectBatch(query, tenantId, context, auditor);
            }
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

    /**
     * Same mandatory tenant predicate on a joined tenant table (INNER / OUTER /
     * CROSS). A foreign {@code tenant_id} on the join is {@code CROSS_TENANT_JOIN}
     * and never reaches the renderer.
     */
    public static QueryJoin injectJoin(
            QueryJoin join,
            TableDescriptor table,
            AccessContext context,
            IsolationAuditor auditor
    ) {
        if (context == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, null, "missing context");
        }
        if (!table.tenantRequired() || context instanceof PlatformContext) {
            return join;
        }
        if (!(context instanceof TenantContext tenant)) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, context, "tenant context required");
        }
        Object filtered = join.filters().get("tenant_id");
        if (filtered != null && !tenant.tenantId().equals(filtered)) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "join tenant_id mismatch");
        }
        Map<String, Object> filters = new LinkedHashMap<>(join.filters());
        filters.put("tenant_id", tenant.tenantId());
        return join.withFilters(filters);
    }

    /**
     * Same mandatory tenant predicate on one IN / EXISTS subquery level. A
     * foreign {@code tenant_id} is {@code CROSS_TENANT_SUBQUERY} and never
     * reaches the renderer. Nested children are injected by the compiler
     * through this method at every depth.
     */
    public static QuerySubquery injectSubquery(
            QuerySubquery subquery,
            TableDescriptor table,
            AccessContext context,
            IsolationAuditor auditor
    ) {
        if (context == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, null, "missing context");
        }
        if (!table.tenantRequired() || context instanceof PlatformContext) {
            return subquery;
        }
        if (!(context instanceof TenantContext tenant)) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, context, "tenant context required");
        }
        Object filtered = subquery.filters().get("tenant_id");
        if (filtered != null && !tenant.tenantId().equals(filtered)) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_SUBQUERY, context, "subquery tenant_id mismatch");
        }
        Map<String, Object> filters = new LinkedHashMap<>(subquery.filters());
        filters.put("tenant_id", tenant.tenantId());
        return subquery.withFilters(filters);
    }

    private static QueryAst injectBatch(
            QueryAst query,
            String tenantId,
            AccessContext context,
            IsolationAuditor auditor
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : query.batchRows()) {
            Object provided = row.get("tenant_id");
            if (provided != null && !tenantId.equals(provided)) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_BATCH, context, "batch tenant_id mismatch");
            }
            Map<String, Object> values = new LinkedHashMap<>(row);
            values.put("tenant_id", tenantId);
            rows.add(values);
        }
        return query.withBatchRows(rows).withValues(rows.getFirst());
    }
}
