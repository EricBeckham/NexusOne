package one.nexus.permission.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;

/**
 * Compiles a P0-02 Predicate to parameterized PostgreSQL. Identifiers are
 * allowlisted; values are never interpolated.
 */
public final class PredicateSqlCompiler {
    public static final Set<String> DEFAULT_COLUMNS = Set.of(
            "id", "owner_id", "department_id", "status", "tenant_id", "title", "phone", "secret");
    public static final Set<String> OPERATORS = Set.of("=", "IN");

    private PredicateSqlCompiler() {}

    public static BoundSql compile(Predicate predicate) {
        return compile(predicate, DEFAULT_COLUMNS);
    }

    public static BoundSql compile(Predicate predicate, Set<String> allowedColumns) {
        List<Object> params = new ArrayList<>();
        String sql = compile(predicate, params, allowedColumns == null ? DEFAULT_COLUMNS : allowedColumns);
        return new BoundSql(sql, List.copyOf(params));
    }

    private static String compile(Predicate predicate, List<Object> params, Set<String> allowedColumns) {
        return switch (predicate.op()) {
            case "true" -> "TRUE";
            case "false" -> "FALSE";
            case "and", "or" -> {
                String joiner = "and".equals(predicate.op()) ? " AND " : " OR ";
                List<String> parts = new ArrayList<>();
                for (Object arg : predicate.args()) {
                    parts.add(compile((Predicate) arg, params, allowedColumns));
                }
                yield "(" + String.join(joiner, parts) + ")";
            }
            case "atom" -> atom(predicate, params, allowedColumns);
            default -> throw PermissionException.badRequest("P2.PERM.0008", "rejected predicate op: " + predicate.op());
        };
    }

    private static String atom(Predicate predicate, List<Object> params, Set<String> allowedColumns) {
        String column = String.valueOf(predicate.args().get(0));
        String operator = String.valueOf(predicate.args().get(1));
        Object value = predicate.args().get(2);
        if (!allowedColumns.contains(column) || !OPERATORS.contains(operator)) {
            throw PermissionException.badRequest("P2.PERM.0008", "rejected identifier or operator");
        }
        if (!column.matches("[a-z_][a-z0-9_]*")) {
            throw PermissionException.badRequest("P2.PERM.0008", "rejected identifier or operator");
        }
        Collection<?> values = "IN".equals(operator)
                ? (value instanceof Collection<?> collection ? collection : List.of(value))
                : List.of(value);
        if (values.isEmpty()) {
            return "FALSE";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : values) {
            params.add(item);
            parts.add(column + " = ?");
        }
        return parts.size() == 1 ? parts.getFirst() : "(" + String.join(" OR ", parts) + ")";
    }
}
