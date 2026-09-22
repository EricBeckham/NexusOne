package one.nexus.permission.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import one.nexus.permission.support.PermissionException;

/**
 * Shared WHERE for list / detail / total / pagination. Tenant predicate is
 * already in {@code whereSql}; callers must not remove it.
 */
public record ScopedQuery(String whereSql, List<Object> parameters) {
    public ScopedQuery {
        if (whereSql == null || !whereSql.toLowerCase(Locale.ROOT).contains("tenant_id")) {
            throw PermissionException.tenantPredicateRequired("scoped query missing tenant_id");
        }
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
    }

    public BoundSql list(String table, List<String> columns, String orderColumn, int limit, int offset) {
        if (limit < 0 || offset < 0) {
            throw PermissionException.badRequest("P2.PERM.0001", "limit/offset must be >= 0");
        }
        String projection = columns.stream().map(PermissionQueryBinder::requireIdent).collect(Collectors.joining(", "));
        String sql = "SELECT " + projection + " FROM " + PermissionQueryBinder.requireIdent(table)
                + " WHERE " + whereSql
                + " ORDER BY " + PermissionQueryBinder.requireIdent(orderColumn)
                + " LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(parameters);
        params.add(limit);
        params.add(offset);
        return new BoundSql(sql, params);
    }

    public BoundSql count(String table) {
        String sql = "SELECT count(*) FROM " + PermissionQueryBinder.requireIdent(table) + " WHERE " + whereSql;
        return new BoundSql(sql, parameters);
    }

    public BoundSql detail(String table, List<String> columns, String idColumn, Object id) {
        String projection = columns.stream().map(PermissionQueryBinder::requireIdent).collect(Collectors.joining(", "));
        String sql = "SELECT " + projection + " FROM " + PermissionQueryBinder.requireIdent(table)
                + " WHERE " + whereSql + " AND " + PermissionQueryBinder.requireIdent(idColumn) + " = ?";
        List<Object> params = new ArrayList<>(parameters);
        params.add(id);
        return new BoundSql(sql, params);
    }
}
