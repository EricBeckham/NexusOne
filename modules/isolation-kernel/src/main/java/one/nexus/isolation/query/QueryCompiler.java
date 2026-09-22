package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
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
        TableDescriptor table = requireTable(input.table(), context);
        List<TableDescriptor> joinTables = resolveJoinTables(input, table, context);
        validateColumns(input, table, joinTables, context);
        QueryAst injected = TenantInjector.inject(input, table, context, auditor);
        List<QueryJoin> injectedJoins = new ArrayList<>();
        List<Map<String, Object>> joinTenantFiltersBeforeScope = new ArrayList<>();
        for (int i = 0; i < injected.joins().size(); i++) {
            QueryJoin join = TenantInjector.injectJoin(injected.joins().get(i), joinTables.get(i), context, auditor);
            injectedJoins.add(join);
            joinTenantFiltersBeforeScope.add(join.filters());
        }
        injected = injected.withJoins(injectedJoins);
        Map<String, Object> tenantFiltersBeforeScope = injected.filters();
        Object tenantValueBeforeScope = injected.values().get("tenant_id");
        injected = dataScopeInjector.inject(injected, table, context);
        injected = softDeleteInjector.inject(injected, table, context);
        assertTenantPredicatePreserved(input, injected, tenantFiltersBeforeScope, tenantValueBeforeScope, context);
        assertJoinTenantPredicatesPreserved(injected, joinTables, joinTenantFiltersBeforeScope, context);
        TableAccessPlan plan = guard.check(table, injected, context, joinTables);
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

    private void validateColumns(
            QueryAst query, TableDescriptor table, List<TableDescriptor> joinTables, AccessContext context) {
        if (!query.joins().isEmpty() && query.kind() != QueryKind.SELECT) {
            throw new IllegalArgumentException("joins are only allowed on SELECT");
        }
        for (String column : query.projection()) {
            requireProjectedColumn(table, joinTables, column, context);
        }
        for (String column : query.values().keySet()) {
            requireColumn(table, column, context);
        }
        for (String column : query.filters().keySet()) {
            requireColumn(table, column, context);
        }
        for (int i = 0; i < query.joins().size(); i++) {
            QueryJoin join = query.joins().get(i);
            TableDescriptor joinTable = joinTables.get(i);
            requireColumn(table, join.leftColumn(), context);
            requireColumn(joinTable, join.rightColumn(), context);
            for (String column : join.filters().keySet()) {
                requireColumn(joinTable, column, context);
            }
            if (join.filters().containsKey("tenant_id") && join.filters().get("tenant_id") == null) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_READ, context, "null join tenant filter");
            }
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

    private void assertJoinTenantPredicatesPreserved(
            QueryAst after,
            List<TableDescriptor> joinTables,
            List<Map<String, Object>> joinTenantFiltersBeforeScope,
            AccessContext context
    ) {
        for (int i = 0; i < after.joins().size(); i++) {
            if (!joinTables.get(i).tenantRequired()) {
                continue;
            }
            Object before = joinTenantFiltersBeforeScope.get(i).get("tenant_id");
            Object afterValue = after.joins().get(i).filters().get("tenant_id");
            if (before != null && !Objects.equals(before, afterValue)) {
                throw auditor.reject(
                        IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope removed join tenant predicate");
            }
        }
    }

    private List<TableDescriptor> resolveJoinTables(QueryAst query, TableDescriptor primary, AccessContext context) {
        List<TableDescriptor> joinTables = new ArrayList<>();
        for (QueryJoin join : query.joins()) {
            Identifiers.require(join.table());
            if (primary.name().equals(join.table())
                    || joinTables.stream().anyMatch(table -> table.name().equals(join.table()))) {
                throw new IllegalArgumentException("join table already in query: " + join.table());
            }
            joinTables.add(requireTable(join.table(), context));
        }
        return List.copyOf(joinTables);
    }

    private TableDescriptor requireTable(String name, AccessContext context) {
        try {
            return registry.require(name);
        } catch (IsolationException ex) {
            throw auditor.reject(ex.error() == null ? IsolationErrorCode.UNREGISTERED_TABLE : ex.error(), context, name);
        }
    }

    private void requireProjectedColumn(
            TableDescriptor primary, List<TableDescriptor> joinTables, String column, AccessContext context) {
        int dot = column.indexOf('.');
        if (dot < 0) {
            requireColumn(primary, column, context);
            return;
        }
        String tableName = column.substring(0, dot);
        String columnName = column.substring(dot + 1);
        Identifiers.require(tableName);
        TableDescriptor target = primary.name().equals(tableName)
                ? primary
                : joinTables.stream()
                        .filter(table -> table.name().equals(tableName))
                        .findFirst()
                        .orElseThrow(() -> auditor.reject(IsolationErrorCode.UNREGISTERED_TABLE, context, tableName));
        requireColumn(target, columnName, context);
    }

    private void requireColumn(TableDescriptor table, String column, AccessContext context) {
        Identifiers.require(column);
        if (!table.columns().contains(column)) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_COLUMN, context, column);
        }
    }
}
