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
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.QuerySubquery;
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
 * <p>INNER / LEFT / RIGHT / FULL OUTER / CROSS JOIN, IN/EXISTS subquery
 * (including nested subquery-in-subquery), multi-row INSERT, ORDER BY, and
 * LIMIT/OFFSET of registered tables are compiled here. Each tenant-scoped table,
 * including every nested subquery level, gets its own {@code tenant_id} predicate.
 * SELF/DEPT equality atoms and FieldPolicy-visible projections may already be
 * encoded on the AST; {@link DataScopeInjector} may only AND extra predicates
 * and cannot strip {@code tenant_id}.
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
        injected = injected.withSubqueries(injectSubqueries(input, context));
        Map<String, Object> tenantFiltersBeforeScope = injected.filters();
        Object tenantValueBeforeScope = injected.values().get("tenant_id");
        List<QueryJoin> joinsBeforeScope = injected.joins();
        List<QuerySubquery> subqueriesBeforeScope = injected.subqueries();
        List<Map<String, Object>> batchBeforeScope = injected.batchRows();
        injected = dataScopeInjector.inject(injected, table, context);
        injected = softDeleteInjector.inject(injected, table, context);
        assertTenantPredicatePreserved(input, injected, tenantFiltersBeforeScope, tenantValueBeforeScope, context);
        assertJoinTenantsPreserved(joinsBeforeScope, injected, context);
        assertSubqueryTenantsPreserved(subqueriesBeforeScope, injected, context);
        assertBatchTenantsPreserved(batchBeforeScope, injected, context);
        TableAccessPlan plan = guard.check(table, injected, context);
        for (QueryJoin join : injected.joins()) {
            guard.checkJoin(registry.require(join.table()), join, context);
        }
        for (QuerySubquery subquery : injected.subqueries()) {
            checkSubqueryTree(subquery, context);
        }
        return PostgresRenderer.render(injected, table, plan);
    }

    private void checkSubqueryTree(QuerySubquery subquery, AccessContext context) {
        guard.checkSubquery(registry.require(subquery.table()), subquery, context);
        for (QuerySubquery nested : subquery.nested()) {
            checkSubqueryTree(nested, context);
        }
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

    private List<QuerySubquery> injectSubqueries(QueryAst input, AccessContext context) {
        return injectSubqueryTree(input.subqueries(), context);
    }

    private List<QuerySubquery> injectSubqueryTree(List<QuerySubquery> subqueries, AccessContext context) {
        if (subqueries.isEmpty()) {
            return List.of();
        }
        List<QuerySubquery> injected = new ArrayList<>();
        for (QuerySubquery subquery : subqueries) {
            TableDescriptor inner = registry.require(subquery.table());
            QuerySubquery withTenant = TenantInjector.injectSubquery(subquery, inner, context, auditor);
            injected.add(withTenant.withNested(injectSubqueryTree(subquery.nested(), context)));
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
            if (!left.table().equals(right.table())
                    || !left.alias().equals(right.alias())
                    || left.kind() != right.kind()) {
                throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope replaced join");
            }
            Object tenant = left.filters().get("tenant_id");
            if (tenant != null && !Objects.equals(tenant, right.filters().get("tenant_id"))) {
                throw auditor.reject(
                        IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope removed join tenant predicate");
            }
        }
    }

    private void assertSubqueryTenantsPreserved(List<QuerySubquery> before, QueryAst after, AccessContext context) {
        assertSubqueryTenantsPreserved(before, after.subqueries(), context);
    }

    private void assertSubqueryTenantsPreserved(
            List<QuerySubquery> before,
            List<QuerySubquery> after,
            AccessContext context
    ) {
        if (before.size() != after.size()) {
            throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope changed subqueries");
        }
        for (int i = 0; i < before.size(); i++) {
            QuerySubquery left = before.get(i);
            QuerySubquery right = after.get(i);
            if (!left.table().equals(right.table()) || !left.alias().equals(right.alias())) {
                throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "DataScope replaced subquery");
            }
            Object tenant = left.filters().get("tenant_id");
            if (tenant != null && !Objects.equals(tenant, right.filters().get("tenant_id"))) {
                throw auditor.reject(
                        IsolationErrorCode.TENANT_PREDICATE_REQUIRED,
                        context,
                        "DataScope removed subquery tenant predicate");
            }
            assertSubqueryTenantsPreserved(left.nested(), right.nested(), context);
        }
    }

    private void assertBatchTenantsPreserved(List<Map<String, Object>> before, QueryAst after, AccessContext context) {
        if (before.size() != after.batchRows().size()) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_BATCH, context, "DataScope changed batch rows");
        }
        for (int i = 0; i < before.size(); i++) {
            Object tenant = before.get(i).get("tenant_id");
            if (tenant != null && !Objects.equals(tenant, after.batchRows().get(i).get("tenant_id"))) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_BATCH, context, "DataScope changed batch tenant_id");
            }
        }
    }

    private void validateColumns(QueryAst query, TableDescriptor table, AccessContext context) {
        if (!query.joins().isEmpty() && query.kind() != QueryKind.SELECT) {
            throw new IllegalArgumentException("joins are select-only");
        }
        if (!query.batchRows().isEmpty() && query.kind() != QueryKind.INSERT) {
            throw new IllegalArgumentException("batch is insert-only");
        }
        if (!query.subqueries().isEmpty() && query.kind() == QueryKind.INSERT) {
            throw new IllegalArgumentException("subqueries are not allowed on insert");
        }
        if ((!query.sorts().isEmpty() || query.page() != null) && query.kind() != QueryKind.SELECT) {
            throw auditor.reject(
                    query.page() != null ? IsolationErrorCode.UNBOUNDED_PAGE : IsolationErrorCode.INVALID_SORT,
                    context,
                    "sort/page are select-only");
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
        for (QuerySubquery subquery : query.subqueries()) {
            validateSubqueryTree(query, table, aliases, subquery, table, aliases, 1, context);
        }
        for (QuerySort sort : query.sorts()) {
            validateSort(table, aliases, sort, context);
        }
        if (query.page() != null) {
            validatePage(query.page(), context);
        }
        for (Map<String, Object> row : query.batchRows()) {
            validateBatchRow(query, table, row, context);
        }
        if (query.kind() == QueryKind.SELECT && query.projection().isEmpty()) {
            throw new IllegalArgumentException("explicit projection required");
        }
        if ((query.kind() == QueryKind.INSERT || query.kind() == QueryKind.UPDATE)
                && query.values().isEmpty()
                && query.batchRows().isEmpty()) {
            throw new IllegalArgumentException("write values required");
        }
        if (query.kind() != QueryKind.INSERT
                && query.kind() != QueryKind.SELECT
                && query.filters().isEmpty()
                && query.subqueries().isEmpty()) {
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
        if (join.kind().requiresOn()) {
            if (join.on().isEmpty()) {
                throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "join requires equality predicates");
            }
        } else if (!join.on().isEmpty()) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_JOIN, context, "CROSS JOIN cannot carry ON predicates");
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

    private void validateSubqueryTree(
            QueryAst query,
            TableDescriptor primary,
            Map<String, TableDescriptor> outerAliases,
            QuerySubquery subquery,
            TableDescriptor parent,
            Map<String, TableDescriptor> scope,
            int depth,
            AccessContext context
    ) {
        if (depth > QuerySubquery.MAX_NESTING) {
            throw new IllegalArgumentException("subquery nesting exceeds max depth");
        }
        Identifiers.require(subquery.table());
        Identifiers.require(subquery.alias());
        TableDescriptor existing = scope.get(subquery.alias());
        if (existing != null && !subquery.alias().equals(subquery.table()) && !existing.name().equals(subquery.table())) {
            throw auditor.reject(IsolationErrorCode.INVALID_IDENTIFIER, context, "duplicate subquery alias");
        }
        TableDescriptor inner = registry.require(subquery.table());
        if (subquery.kind() == QuerySubquery.Kind.IN) {
            if (subquery.outerColumn().isBlank() || subquery.projection().isBlank()) {
                throw new IllegalArgumentException("IN subquery requires outer and inner columns");
            }
            if (depth == 1) {
                requireProjectedColumn(query, primary, outerAliases, subquery.outerColumn(), context);
            } else {
                requireOuterColumn(parent, scope, subquery.outerColumn(), context);
            }
            requireColumn(inner, subquery.projection(), context);
        }
        Map<String, TableDescriptor> innerScope = new LinkedHashMap<>(scope);
        innerScope.put(subquery.alias(), inner);
        for (JoinOn on : subquery.correlated()) {
            requireJoinSide(innerScope, on.leftAlias(), on.leftColumn(), context);
            requireJoinSide(innerScope, on.rightAlias(), on.rightColumn(), context);
        }
        for (String column : subquery.filters().keySet()) {
            requireColumn(inner, column, context);
        }
        if (subquery.filters().containsKey("tenant_id") && subquery.filters().get("tenant_id") == null) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_SUBQUERY, context, "null subquery tenant filter");
        }
        for (QuerySubquery nested : subquery.nested()) {
            validateSubqueryTree(query, primary, outerAliases, nested, inner, innerScope, depth + 1, context);
        }
    }

    private void requireOuterColumn(
            TableDescriptor parent,
            Map<String, TableDescriptor> scope,
            String column,
            AccessContext context
    ) {
        if (!column.contains(".")) {
            requireColumn(parent, column, context);
            return;
        }
        int dot = column.indexOf('.');
        requireJoinSide(scope, column.substring(0, dot), column.substring(dot + 1), context);
    }

    private void validateSort(
            TableDescriptor primary,
            Map<String, TableDescriptor> aliases,
            QuerySort sort,
            AccessContext context
    ) {
        String column = sort.column();
        String alias = null;
        String name = column;
        int dot = column.indexOf('.');
        if (dot >= 0) {
            alias = column.substring(0, dot);
            name = column.substring(dot + 1);
        }
        try {
            if (alias != null) {
                Identifiers.require(alias);
            }
            Identifiers.require(name);
        } catch (RuntimeException ex) {
            throw auditor.reject(IsolationErrorCode.INVALID_SORT, context, "invalid sort identifier");
        }
        TableDescriptor owner = alias == null ? primary : aliases.get(alias);
        if (owner == null || !owner.columns().contains(name)) {
            throw auditor.reject(IsolationErrorCode.INVALID_SORT, context, "unregistered sort column");
        }
    }

    private void validatePage(QueryPage page, AccessContext context) {
        if (page.limit() < 1 || page.limit() > QueryPage.MAX_LIMIT || page.offset() < 0) {
            throw auditor.reject(IsolationErrorCode.UNBOUNDED_PAGE, context, "page limit/offset out of bounds");
        }
    }

    private void validateBatchRow(
            QueryAst query,
            TableDescriptor table,
            Map<String, Object> row,
            AccessContext context
    ) {
        if (row.isEmpty()) {
            throw new IllegalArgumentException("write values required");
        }
        if (!query.batchRows().isEmpty()) {
            Map<String, Object> first = query.batchRows().getFirst();
            if (!first.keySet().equals(row.keySet())) {
                throw new IllegalArgumentException("batch rows must share the same columns");
            }
        }
        for (String column : row.keySet()) {
            requireColumn(table, column, context);
        }
        if (row.containsKey("tenant_id") && row.get("tenant_id") == null) {
            throw auditor.reject(IsolationErrorCode.CROSS_TENANT_BATCH, context, "null batch tenant_id");
        }
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
            if (column.contains(".")) {
                int dot = column.indexOf('.');
                String alias = column.substring(0, dot);
                String name = column.substring(dot + 1);
                TableDescriptor owner = aliases.get(alias);
                if (owner == null) {
                    throw auditor.reject(IsolationErrorCode.UNREGISTERED_TABLE, context, alias);
                }
                requireColumn(owner, name, context);
                return;
            }
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
