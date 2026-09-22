package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.JoinOn;
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
 *
 * <p>INNER JOIN of registered tables is compiled here. Each tenant-scoped join
 * gets its own {@code tenant_id} predicate. Subquery / batch / sort / page are
 * still out of this compiler.
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
        injected = injected.withJoins(injectJoins(input, context));
        Map<String, Object> tenantFiltersBeforeScope = injected.filters();
        Object tenantValueBeforeScope = injected.values().get("tenant_id");
        List<QueryJoin> joinsBeforeScope = injected.joins();
        injected = dataScopeInjector.inject(injected, table, context);
        injected = softDeleteInjector.inject(injected, table, context);
        assertTenantPredicatePreserved(input, injected, tenantFiltersBeforeScope, tenantValueBeforeScope, context);
        assertJoinTenantsPreserved(joinsBeforeScope, injected, context);
        TableAccessPlan plan = guard.check(table, injected, context);
        for (QueryJoin join : injected.joins()) {
            guard.checkJoin(registry.require(join.table()), join, context);
        }
        return PostgresRenderer.render(injected, table, plan);
    }

    private List<QueryJoin> injectJoins(QueryAst input, AccessContext context) {
        if (input.joins().isEmpty()) {
            return List.of();
        }
        List<QueryJoin> injected = new ArrayList<>();
        for (QueryJoin join : input.joins()) {
            TableDescriptor joined = registry.require(join.table());
            injected.add(TenantInjector.injectJoin(join, joined, context, auditor));
        }
        return List.copyOf(injected);
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

    private void assertJoinTenantsPreserved(List<QueryJoin> before, QueryAst after, AccessContext context) {
        if (before.size() != after.joins().size()) {
            throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope changed joins");
        }
        for (int i = 0; i < before.size(); i++) {
            QueryJoin left = before.get(i);
            QueryJoin right = after.joins().get(i);
            if (!left.table().equals(right.table()) || !left.alias().equals(right.alias())) {
                throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope replaced join");
            }
            Object tenant = left.filters().get("tenant_id");
            if (tenant != null && !Objects.equals(tenant, right.filters().get("tenant_id"))) {
                throw auditor.reject(
                        IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope removed join tenant predicate");
            }
        }
    }

    private void validateColumns(QueryAst query, TableDescriptor table, AccessContext context) {
        if (!query.joins().isEmpty() && query.kind() != QueryKind.SELECT) {
            throw new IllegalArgumentException("joins are select-only");
        }
        Map<String, TableDescriptor> aliases = aliases(query, table, context);
        for (String column : query.projection()) {
            requireProjectedColumn(query, table, aliases, column, context);
        }
        for (String column : query.values().keySet()) {
            requireColumn(table, column, context);
        }
        for (String column : query.filters().keySet()) {
            requireColumn(table, column, context);
        }
        for (QueryJoin join : query.joins()) {
            validateJoin(query, table, aliases, join, context);
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

    private void validateJoin(
            QueryAst query,
            TableDescriptor primary,
            Map<String, TableDescriptor> aliases,
            QueryJoin join,
            AccessContext context
    ) {
        Identifiers.require(join.table());
        Identifiers.require(join.alias());
        TableDescriptor joined = aliases.get(join.alias());
        if (joined == null) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_TABLE, context, join.alias());
        }
        if (join.on().isEmpty()) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "join requires equality predicates");
        }
        for (JoinOn on : join.on()) {
            requireJoinSide(aliases, on.leftAlias(), on.leftColumn(), context);
            requireJoinSide(aliases, on.rightAlias(), on.rightColumn(), context);
            if (!on.leftAlias().equals(query.table()) && !on.leftAlias().equals(join.alias())
                    && !on.rightAlias().equals(query.table()) && !on.rightAlias().equals(join.alias())) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "join must reference its tables");
            }
        }
        for (String column : join.filters().keySet()) {
            requireColumn(joined, column, context);
        }
        if (join.filters().containsKey("tenant_id") && join.filters().get("tenant_id") == null) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "null join tenant filter");
        }
        Objects.requireNonNull(primary, "primary");
    }

    private Map<String, TableDescriptor> aliases(QueryAst query, TableDescriptor primary, AccessContext context) {
        Map<String, TableDescriptor> aliases = new LinkedHashMap<>();
        aliases.put(primary.name(), primary);
        for (QueryJoin join : query.joins()) {
            Identifiers.require(join.alias());
            if (aliases.containsKey(join.alias())) {
                throw auditor.reject(IsolationErrorCode.INVALID_IDENTIFIER, context, "duplicate join alias");
            }
            aliases.put(join.alias(), registry.require(join.table()));
        }
        return aliases;
    }

    private void requireJoinSide(
            Map<String, TableDescriptor> aliases,
            String alias,
            String column,
            AccessContext context
    ) {
        Identifiers.require(alias);
        TableDescriptor owner = aliases.get(alias);
        if (owner == null) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_TABLE, context, alias);
        }
        requireColumn(owner, column, context);
    }

    private void requireProjectedColumn(
            QueryAst query,
            TableDescriptor primary,
            Map<String, TableDescriptor> aliases,
            String column,
            AccessContext context
    ) {
        if (query.joins().isEmpty() || !column.contains(".")) {
            requireColumn(primary, column, context);
            return;
        }
        int dot = column.indexOf('.');
        String alias = column.substring(0, dot);
        String name = column.substring(dot + 1);
        TableDescriptor owner = aliases.get(alias);
        if (owner == null) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_TABLE, context, alias);
        }
        requireColumn(owner, name, context);
    }

    private void requireColumn(TableDescriptor table, String column, AccessContext context) {
        Identifiers.require(column);
        if (!table.columns().contains(column)) {
            throw auditor.reject(IsolationErrorCode.UNREGISTERED_COLUMN, context, column);
        }
    }
}
