package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TableAccessPlan;
import one.nexus.isolation.api.TableDescriptor;

public final class PostgresRenderer {
    private PostgresRenderer() {}

    public static RenderedSql render(QueryAst query, TableDescriptor table, TableAccessPlan plan) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        if (query.kind() == QueryKind.SELECT) {
            sql.append("SELECT ");
            appendNames(sql, query.projection());
            sql.append(" FROM ").append(Identifiers.quote(table.name()));
            where(sql, query.filters(), args);
        } else if (query.kind() == QueryKind.INSERT) {
            sql.append("INSERT INTO ").append(Identifiers.quote(table.name())).append(" (");
            appendNames(sql, query.values().keySet());
            sql.append(") VALUES (");
            placeholders(sql, query.values().values(), args);
            sql.append(')');
        } else if (query.kind() == QueryKind.UPDATE) {
            sql.append("UPDATE ").append(Identifiers.quote(table.name())).append(" SET ");
            int i = 0;
            for (Map.Entry<String, Object> entry : query.values().entrySet()) {
                if (i++ > 0) {
                    sql.append(", ");
                }
                sql.append(Identifiers.quote(entry.getKey())).append(" = ?");
                args.add(entry.getValue());
            }
            where(sql, query.filters(), args);
        } else {
            sql.append("DELETE FROM ").append(Identifiers.quote(table.name()));
            where(sql, query.filters(), args);
        }
        return new RenderedSql(sql.toString(), args, plan);
    }

    private static void where(StringBuilder sql, Map<String, Object> filters, List<Object> args) {
        if (filters.isEmpty()) {
            return;
        }
        sql.append(" WHERE ");
        int i = 0;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            sql.append(Identifiers.quote(entry.getKey()));
            if (entry.getValue() == null) {
                sql.append(" IS NULL");
            } else {
                sql.append(" = ?");
                args.add(entry.getValue());
            }
        }
    }

    private static void appendNames(StringBuilder sql, Iterable<String> names) {
        int i = 0;
        for (String name : names) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append(Identifiers.quote(name));
        }
    }

    private static void placeholders(StringBuilder sql, Iterable<Object> values, List<Object> args) {
        int i = 0;
        for (Object value : values) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append('?');
            args.add(value);
        }
    }
}
