package one.nexus.isolation.query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TableAccessPlan;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.audit.IsolationAuditor;
import one.nexus.isolation.spi.DataScopeInjector;
import one.nexus.isolation.spi.SoftDeleteInjector;

/**
 * Compile pipeline: registry → tenant inject (cannot disable) → optional DataScope/soft-delete
 * → TableAccessPlan Guard → parameterized PostgreSQL renderer.
 */
public final class QueryCompiler {
    private final TableRegistry registry;
    private final TableAccessPlanGuard guard;
    private final IsolationAuditor auditor;
    private final DataScopeInjector dataScopeInjector;
    private final SoftDeleteInjector softDeleteInjector;

    public QueryCompiler(
            TableRegistry registry,
            TableAccessPlanGuard guard,
            IsolationAuditor auditor,
            DataScopeInjector dataScopeInjector,
            SoftDeleteInjector softDeleteInjector
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.dataScopeInjector = dataScopeInjector == null ? DataScopeInjector.passthrough() : dataScopeInjector;
        this.softDeleteInjector = softDeleteInjector == null ? SoftDeleteInjector.passthrough() : softDeleteInjector;
    }

    public RenderedSql compile(QueryAst input, AccessContext context) {
        if (context == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, null, "missing context");
        }
        Identifiers.require(input.table());
        TableDescriptor table = registry.require(input.table());
        validateColumns(input, table, context);
        QueryAst injected = TenantInjector.inject(input, table, context, auditor);
        Map<String, Object> tenantFiltersBeforeScope = injected.filters();
        Object tenantValueBeforeScope = injected.values().get("tenant_id");
        injected = dataScopeInjector.inject(injected, table, context);
        injected = softDeleteInjector.inject(injected, table, context);
        assertTenantPredicatePreserved(input, injected, tenantFiltersBeforeScope, tenantValueBeforeScope, context);
        TableAccessPlan plan = guard.check(table, injected, context);
        return PostgresRenderer.render(injected, table, plan);
    }

    private void assertTenantPredicatePreserved(
            QueryAst original,
            QueryAst after,
            Map<String, Object> tenantFiltersBeforeScope,
            Object tenantValueBeforeScope,
            AccessContext context
    ) {
        if (original.kind() == QueryKind.INSERT) {
            if (tenantValueBeforeScope != null && !Objects.equals(tenantValueBeforeScope, after.values().get("tenant_id"))) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_WRITE, context, "DataScope changed tenant_id");
            }
            return;
        }
        Object before = tenantFiltersBeforeScope.get("tenant_id");
        if (before != null && !Objects.equals(before, after.filters().get("tenant_id"))) {
            throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope removed tenant predicate");
        }
    }

    private void validateColumns(QueryAst query, TableDescriptor table, AccessContext context) {
        for (String column : query.projection()) {
            requireColumn(table, column, context);
        }
        for (String column : query.values().keySet()) {
            requireColumn(table, column, context);
        }
        for (String column : query.filters().keySet()) {
            requireColumn(table, column, context);
        }
        if (query.kind() == QueryKind.SELECT && query.projection().isEmpty()) {
            throw new IllegalArgumentException("explicit projection required");
        }
        if ((query.kind() == QueryKind.INSERT || query.kind() == QueryKind.UPDATE) && query.values().isEmpty()) {
            throw new IllegalArgumentException("write values required");
        }
        if (query.kind() != QueryKind.INSERT && query.kind() != QueryKind.SELECT && query.filters().isEmpty()) {
            throw auditor.reject(IsolationErrorCode.UNBOUNDED_WRITE, context, "write requires a business key");
        }
        if (query.kind() != QueryKind.INSERT && query.values().containsKey("tenant_id")) {
            throw auditor.reject(IsolationErrorCode.TENANT_ID_IMMUTABLE, context, "cannot update tenant_id");
        }
        if (query.filters().containsKey("tenant_id") && query.filters().get("tenant_id") == null) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_READ, context, "null tenant filter");
        }
    }

    private void requireColumn(TableDescriptor table, String column, AccessContext context) {
        Identifiers.require(column);
        if (!table.columns().contains(column)) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_COLUMN, context, column);
        }
    }
}
