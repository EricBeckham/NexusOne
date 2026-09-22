package one.nexus.permission.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.QueryOr;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;

/**
 * Encodes safe SELF/DEPT DataScope equality atoms, single-column multi-value
 * {@code IN} lists ({@code DEPT_AND_CHILDREN} / SPECIFIED), mixed-column OR
 * (SELF∨DEPT) onto {@link QueryAst#disjunctions()}, and FieldPolicy visibility
 * into a Query AST. TenantInjector still owns {@code tenant_id}. Unencodable
 * leftovers (empty {@code IN}, unsupported operators, AND-of-OR) stay on
 * {@link PermissionQueryBinder}; masks stay on {@code FieldPolicyProjector}.
 */
public final class QueryAstPolicyCompiler {
    public static final Set<String> ENCODABLE_COLUMNS = Set.of("owner_id", "department_id", "id", "status");

    private QueryAstPolicyCompiler() {}

    public record Encoding(
            QueryAst query,
            boolean scopeFullyEncoded,
            Predicate leftoverScope,
            List<String> droppedProjection,
            boolean visibilityEncoded,
            Map<String, FieldRule> leftoverMaskFields
    ) {
        public Encoding {
            Objects.requireNonNull(query, "query");
            leftoverScope = leftoverScope == null ? Predicate.TRUE : leftoverScope;
            droppedProjection = List.copyOf(droppedProjection == null ? List.of() : droppedProjection);
            leftoverMaskFields = Map.copyOf(leftoverMaskFields == null ? Map.of() : leftoverMaskFields);
        }
    }

    public static Encoding encode(QueryAst query, Decision decision, TableDescriptor table) {
        Objects.requireNonNull(query, "query");
        if (decision == null || !decision.allowed()) {
            return new Encoding(query, false, Predicate.FALSE, List.of(), false, decision == null ? Map.of() : decision.fields());
        }
        return encode(query, decision.scope(), decision.fields(), table);
    }

    public static Map<String, Object> equalityFilters(Predicate scope, Set<String> columns) {
        Set<String> allowed = columns == null ? Set.of() : Set.copyOf(columns);
        Map<String, Object> filters = new LinkedHashMap<>();
        List<Predicate> leftover = new ArrayList<>();
        collectSafeEquals(scope == null ? Predicate.FALSE : scope, allowed, filters, new ArrayList<>(), leftover);
        return Map.copyOf(filters);
    }

    public static Encoding encode(
            QueryAst query,
            Predicate scope,
            Map<String, FieldRule> fields,
            TableDescriptor table
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(table, "table");
        ScopePlan scopePlan = planScope(scope == null ? Predicate.FALSE : scope, table.columns());
        QueryAst next = query;
        if (query.kind() != QueryKind.INSERT && !scopePlan.filters().isEmpty()) {
            Map<String, Object> filters = new LinkedHashMap<>(query.filters());
            scopePlan.filters().forEach((column, value) -> {
                Object existing = filters.get(column);
                if (existing != null && !Objects.equals(existing, value)) {
                    throw PermissionException.conflict("DataScope filter conflict on " + column);
                }
                filters.put(column, value);
            });
            next = next.withFilters(filters);
        }
        if (query.kind() != QueryKind.INSERT && !scopePlan.disjunctions().isEmpty()) {
            List<QueryOr> disjunctions = new ArrayList<>(next.disjunctions());
            disjunctions.addAll(scopePlan.disjunctions());
            next = next.withDisjunctions(disjunctions);
        }
        VisibilityPlan visibility = planVisibility(next, fields);
        if (visibility.encoded()) {
            next = next.withProjection(visibility.projection());
        }
        return new Encoding(
                next,
                query.kind() != QueryKind.INSERT && scopePlan.leftover().equals(Predicate.TRUE),
                query.kind() == QueryKind.INSERT ? (scope == null ? Predicate.FALSE : scope) : scopePlan.leftover(),
                visibility.dropped(),
                visibility.encoded(),
                visibility.leftoverMasks());
    }

    private static ScopePlan planScope(Predicate scope, Set<String> columns) {
        Map<String, Object> filters = new LinkedHashMap<>();
        List<QueryOr> disjunctions = new ArrayList<>();
        List<Predicate> leftover = new ArrayList<>();
        collectSafeEquals(scope, columns, filters, disjunctions, leftover);
        Predicate rest = leftover.isEmpty() ? Predicate.TRUE : Predicate.allOf(leftover);
        return new ScopePlan(filters, List.copyOf(disjunctions), rest);
    }

    private static void collectSafeEquals(
            Predicate predicate,
            Set<String> columns,
            Map<String, Object> filters,
            List<QueryOr> disjunctions,
            List<Predicate> leftover
    ) {
        switch (predicate.op()) {
            case "true" -> {
                // ALL: no extra filter.
            }
            case "false" -> leftover.add(predicate);
            case "and" -> {
                for (Object arg : predicate.args()) {
                    collectSafeEquals((Predicate) arg, columns, filters, disjunctions, leftover);
                }
            }
            case "or" -> {
                if (!encodeSameColumnIn(predicate, columns, filters, leftover)
                        && !encodeMixedColumnOr(predicate, columns, disjunctions)) {
                    leftover.add(predicate);
                }
            }
            case "atom" -> encodeAtom(predicate, columns, filters, leftover);
            default -> leftover.add(predicate);
        }
    }

    private static boolean encodeSameColumnIn(
            Predicate predicate,
            Set<String> columns,
            Map<String, Object> filters,
            List<Predicate> leftover
    ) {
        String column = null;
        LinkedHashMap<Object, Boolean> values = new LinkedHashMap<>();
        for (Object arg : predicate.args()) {
            if (!(arg instanceof Predicate child) || !"atom".equals(child.op()) || child.args().size() < 3) {
                return false;
            }
            String childColumn = String.valueOf(child.args().get(0));
            Object encoded = filterValue(String.valueOf(child.args().get(1)), child.args().get(2));
            if (encoded == null
                    || "tenant_id".equals(childColumn)
                    || !ENCODABLE_COLUMNS.contains(childColumn)
                    || !columns.contains(childColumn)) {
                return false;
            }
            if (column == null) {
                column = childColumn;
            } else if (!column.equals(childColumn)) {
                return false;
            }
            if (encoded instanceof Collection<?> collection) {
                collection.forEach(item -> values.put(item, Boolean.TRUE));
            } else {
                values.put(encoded, Boolean.TRUE);
            }
        }
        if (column == null || values.isEmpty()) {
            return false;
        }
        Object next = values.size() == 1
                ? values.keySet().iterator().next()
                : List.copyOf(values.keySet());
        Object existing = filters.get(column);
        if (existing != null && !Objects.equals(existing, next)) {
            leftover.add(predicate);
            return true;
        }
        filters.put(column, next);
        return true;
    }

    /**
     * SELF∨DEPT and other mixed-column ORs of encodable atoms / same-column IN.
     * Nested mixed OR flattens. AND-of-OR and unencodable children stay leftover.
     */
    private static boolean encodeMixedColumnOr(
            Predicate predicate,
            Set<String> columns,
            List<QueryOr> disjunctions
    ) {
        List<Map<String, Object>> branches = new ArrayList<>();
        if (!collectOrBranches(predicate, columns, branches) || branches.size() < 2) {
            return false;
        }
        List<Map<String, Object>> merged = mergeSameColumnBranches(branches);
        if (merged.size() < 2) {
            return false;
        }
        disjunctions.add(QueryOr.of(merged));
        return true;
    }

    private static boolean collectOrBranches(
            Predicate predicate,
            Set<String> columns,
            List<Map<String, Object>> branches
    ) {
        if ("or".equals(predicate.op())) {
            for (Object arg : predicate.args()) {
                if (!(arg instanceof Predicate child) || !collectOrBranches(child, columns, branches)) {
                    return false;
                }
            }
            return !branches.isEmpty();
        }
        Map<String, Object> branch = encodeBranch(predicate, columns);
        if (branch == null || branch.isEmpty()) {
            return false;
        }
        branches.add(branch);
        return true;
    }

    private static Map<String, Object> encodeBranch(Predicate predicate, Set<String> columns) {
        if ("atom".equals(predicate.op())) {
            return encodeAtomBranch(predicate, columns);
        }
        if ("and".equals(predicate.op())) {
            Map<String, Object> branch = new LinkedHashMap<>();
            for (Object arg : predicate.args()) {
                if (!(arg instanceof Predicate child) || !"atom".equals(child.op())) {
                    return null;
                }
                Map<String, Object> atom = encodeAtomBranch(child, columns);
                if (atom == null) {
                    return null;
                }
                for (Map.Entry<String, Object> entry : atom.entrySet()) {
                    Object existing = branch.get(entry.getKey());
                    if (existing != null && !Objects.equals(existing, entry.getValue())) {
                        return null;
                    }
                    branch.put(entry.getKey(), entry.getValue());
                }
            }
            return branch.isEmpty() ? null : branch;
        }
        if ("or".equals(predicate.op())) {
            Map<String, Object> collapsed = new LinkedHashMap<>();
            List<Predicate> leftover = new ArrayList<>();
            if (!encodeSameColumnIn(predicate, columns, collapsed, leftover) || collapsed.size() != 1) {
                return null;
            }
            return collapsed;
        }
        return null;
    }

    private static Map<String, Object> encodeAtomBranch(Predicate predicate, Set<String> columns) {
        if (predicate.args().size() < 3) {
            return null;
        }
        String column = String.valueOf(predicate.args().get(0));
        String operator = String.valueOf(predicate.args().get(1));
        Object value = predicate.args().get(2);
        if ("tenant_id".equals(column)) {
            throw PermissionException.tenantPredicateRequired(
                    "DataScope must not author tenant_id; TenantInjector owns it");
        }
        Object encoded = filterValue(operator, value);
        if (encoded == null || !ENCODABLE_COLUMNS.contains(column) || !columns.contains(column)) {
            return null;
        }
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put(column, encoded);
        return branch;
    }

    private static List<Map<String, Object>> mergeSameColumnBranches(List<Map<String, Object>> branches) {
        LinkedHashMap<String, LinkedHashMap<Object, Boolean>> singles = new LinkedHashMap<>();
        List<Map<String, Object>> multi = new ArrayList<>();
        for (Map<String, Object> branch : branches) {
            if (branch.size() != 1) {
                multi.add(branch);
                continue;
            }
            Map.Entry<String, Object> only = branch.entrySet().iterator().next();
            LinkedHashMap<Object, Boolean> values = singles.computeIfAbsent(only.getKey(), key -> new LinkedHashMap<>());
            if (only.getValue() instanceof Collection<?> collection) {
                collection.forEach(item -> values.put(item, Boolean.TRUE));
            } else {
                values.put(only.getValue(), Boolean.TRUE);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        singles.forEach((column, values) -> {
            Object next = values.size() == 1
                    ? values.keySet().iterator().next()
                    : List.copyOf(values.keySet());
            Map<String, Object> branch = new LinkedHashMap<>();
            branch.put(column, next);
            merged.add(branch);
        });
        merged.addAll(multi);
        return merged;
    }

    private static void encodeAtom(
            Predicate predicate,
            Set<String> columns,
            Map<String, Object> filters,
            List<Predicate> leftover
    ) {
        String column = String.valueOf(predicate.args().get(0));
        String operator = String.valueOf(predicate.args().get(1));
        Object value = predicate.args().get(2);
        if ("tenant_id".equals(column)) {
            throw PermissionException.tenantPredicateRequired(
                    "DataScope must not author tenant_id; TenantInjector owns it");
        }
        Object encoded = filterValue(operator, value);
        if (encoded != null
                && ENCODABLE_COLUMNS.contains(column)
                && columns.contains(column)) {
            Object existing = filters.get(column);
            if (existing != null && !Objects.equals(existing, encoded)) {
                leftover.add(predicate);
                return;
            }
            filters.put(column, encoded);
            return;
        }
        leftover.add(predicate);
    }

    private static Object filterValue(String operator, Object value) {
        if ("=".equals(operator) && !(value instanceof Collection<?>)) {
            return value;
        }
        if (!"IN".equals(operator)) {
            return null;
        }
        Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
        if (values.isEmpty()) {
            return null;
        }
        List<Object> items = new ArrayList<>();
        for (Object item : values) {
            if (item == null || item instanceof Collection<?>) {
                return null;
            }
            items.add(item);
        }
        return items.size() == 1 ? items.getFirst() : List.copyOf(items);
    }

    private static VisibilityPlan planVisibility(QueryAst query, Map<String, FieldRule> fields) {
        if (query.kind() != QueryKind.SELECT || fields == null || fields.isEmpty()) {
            return new VisibilityPlan(query.projection(), List.of(), Map.of(), false);
        }
        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        Map<String, FieldRule> leftoverMasks = new LinkedHashMap<>();
        for (String column : query.projection()) {
            FieldRule rule = ruleFor(column, fields);
            if (rule != null && !rule.visible()) {
                dropped.add(column);
                continue;
            }
            kept.add(column);
            if (rule != null && needsProjector(rule)) {
                leftoverMasks.put(fieldName(column), rule);
            }
        }
        if (kept.isEmpty()) {
            throw PermissionException.badRequest("P2.PERM.0008", "FieldPolicy left an empty projection");
        }
        return new VisibilityPlan(List.copyOf(kept), List.copyOf(dropped), leftoverMasks, true);
    }

    private static boolean needsProjector(FieldRule rule) {
        return rule.mask() != Mask.NONE || rule.secondAuthorization();
    }

    private static FieldRule ruleFor(String column, Map<String, FieldRule> fields) {
        FieldRule direct = fields.get(column);
        if (direct != null) {
            return direct;
        }
        return fields.get(fieldName(column));
    }

    static String fieldName(String column) {
        int dot = column == null ? -1 : column.lastIndexOf('.');
        return dot < 0 ? column : column.substring(dot + 1);
    }

    private record ScopePlan(Map<String, Object> filters, List<QueryOr> disjunctions, Predicate leftover) {}

    private record VisibilityPlan(
            List<String> projection,
            List<String> dropped,
            Map<String, FieldRule> leftoverMasks,
            boolean encoded
    ) {}
}
