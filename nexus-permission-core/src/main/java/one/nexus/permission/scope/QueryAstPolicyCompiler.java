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
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;

/**
 * Encodes safe SELF/DEPT DataScope equality atoms and FieldPolicy visibility
 * into a Query AST. TenantInjector still owns {@code tenant_id}. OR / multi-value
 * IN trees stay on {@link PermissionQueryBinder}; masks stay on
 * {@code FieldPolicyProjector}.
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
        collectSafeEquals(scope == null ? Predicate.FALSE : scope, allowed, filters, leftover);
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
        List<Predicate> leftover = new ArrayList<>();
        collectSafeEquals(scope, columns, filters, leftover);
        Predicate rest = leftover.isEmpty() ? Predicate.TRUE : Predicate.allOf(leftover);
        return new ScopePlan(filters, rest);
    }

    private static void collectSafeEquals(
            Predicate predicate,
            Set<String> columns,
            Map<String, Object> filters,
            List<Predicate> leftover
    ) {
        switch (predicate.op()) {
            case "true" -> {
                // ALL: no extra filter.
            }
            case "false" -> leftover.add(predicate);
            case "and" -> {
                for (Object arg : predicate.args()) {
                    collectSafeEquals((Predicate) arg, columns, filters, leftover);
                }
            }
            case "or" -> leftover.add(predicate);
            case "atom" -> encodeAtom(predicate, columns, filters, leftover);
            default -> leftover.add(predicate);
        }
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
        Object equality = equalityValue(operator, value);
        if (equality != null
                && ENCODABLE_COLUMNS.contains(column)
                && columns.contains(column)) {
            Object existing = filters.get(column);
            if (existing != null && !Objects.equals(existing, equality)) {
                leftover.add(predicate);
                return;
            }
            filters.put(column, equality);
            return;
        }
        leftover.add(predicate);
    }

    private static Object equalityValue(String operator, Object value) {
        if ("=".equals(operator) && !(value instanceof Collection<?>)) {
            return value;
        }
        if ("IN".equals(operator)) {
            Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
            if (values.size() == 1) {
                return values.iterator().next();
            }
        }
        return null;
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

    private record ScopePlan(Map<String, Object> filters, Predicate leftover) {}

    private record VisibilityPlan(
            List<String> projection,
            List<String> dropped,
            Map<String, FieldRule> leftoverMasks,
            boolean encoded
    ) {}
}
