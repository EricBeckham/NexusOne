package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import one.nexus.isolation.api.JoinOn;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.QueryOr;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.QuerySubquery;
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
            String primaryAlias = query.joins().isEmpty() ? null : Identifiers.tableAlias(table.name());
            if (query.joins().isEmpty()) {
                appendNames(sql, query.projection());
                sql.append(" FROM ").append(Identifiers.quoteTable(table.name()));
            } else {
                appendQualifiedProjection(sql, query.projection(), primaryAlias);
                sql.append(" FROM ").append(Identifiers.quoteTable(table.name()))
                        .append(' ').append(Identifiers.quote(primaryAlias));
                for (QueryJoin join : query.joins()) {
                    appendJoin(sql, join, args);
                }
            }
            appendPredicates(sql, primaryAlias, query.filters(), query.disjunctions(), query.subqueries(), query.joins(), args);
            appendOrderBy(sql, primaryAlias, query.sorts());
            appendPage(sql, query.page(), args);
        } else if (query.kind() == QueryKind.INSERT) {
            if (!query.batchRows().isEmpty()) {
                appendBatchInsert(sql, table, query.batchRows(), args);
            } else {
                sql.append("INSERT INTO ").append(Identifiers.quoteTable(table.name())).append(" (");
                appendNames(sql, query.values().keySet());
                sql.append(") VALUES (");
                placeholders(sql, query.values(), args);
                sql.append(')');
            }
        } else if (query.kind() == QueryKind.UPDATE) {
            sql.append("UPDATE ").append(Identifiers.quoteTable(table.name())).append(" SET ");
            int i = 0;
            for (Map.Entry<String, Object> entry : query.values().entrySet()) {
                if (i++ > 0) {
                    sql.append(", ");
                }
                sql.append(Identifiers.quote(entry.getKey())).append(" = ?");
                args.add(FilterAtoms.bind(entry.getKey(), entry.getValue()));
            }
            appendPredicates(sql, null, query.filters(), query.disjunctions(), query.subqueries(), query.joins(), args);
        } else {
            sql.append("DELETE FROM ").append(Identifiers.quoteTable(table.name()));
            appendPredicates(sql, null, query.filters(), query.disjunctions(), query.subqueries(), query.joins(), args);
        }
        return new RenderedSql(sql.toString(), args, plan);
    }

    private static void appendBatchInsert(
            StringBuilder sql,
            TableDescriptor table,
            List<Map<String, Object>> rows,
            List<Object> args
    ) {
        Map<String, Object> first = rows.getFirst();
        sql.append("INSERT INTO ").append(Identifiers.quoteTable(table.name())).append(" (");
        appendNames(sql, first.keySet());
        sql.append(") VALUES ");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sql.append(", ");
            }
            sql.append('(');
            placeholders(sql, rows.get(r), args);
            sql.append(')');
        }
    }

    private static void appendJoin(StringBuilder sql, QueryJoin join, List<Object> args) {
        sql.append(' ').append(join.kind().sql()).append(' ')
                .append(Identifiers.quoteTable(join.table()))
                .append(' ').append(Identifiers.quote(join.alias()));
        if (!join.kind().requiresOn()) {
            return;
        }
        sql.append(" ON ");
        int i = 0;
        for (JoinOn on : join.on()) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            sql.append(Identifiers.quote(on.leftAlias())).append('.').append(Identifiers.quote(on.leftColumn()))
                    .append(" = ")
                    .append(Identifiers.quote(on.rightAlias())).append('.').append(Identifiers.quote(on.rightColumn()));
        }
        for (Map.Entry<String, Object> entry : join.filters().entrySet()) {
            sql.append(" AND ").append(Identifiers.quote(join.alias())).append('.').append(Identifiers.quote(entry.getKey()));
            FilterAtoms.append(sql, args, entry.getKey(), entry.getValue());
        }
    }

    private static void appendQualifiedProjection(StringBuilder sql, List<String> projection, String primaryAlias) {
        int i = 0;
        for (String column : projection) {
            if (i++ > 0) {
                sql.append(", ");
            }
            appendColumn(sql, primaryAlias, column);
        }
    }

    private static void appendPredicates(
            StringBuilder sql,
            String alias,
            Map<String, Object> filters,
            List<QueryOr> disjunctions,
            List<QuerySubquery> subqueries,
            List<QueryJoin> joins,
            List<Object> args
    ) {
        List<QueryJoin> cross = joins.stream().filter(join -> !join.kind().requiresOn()).toList();
        if (filters.isEmpty()
                && disjunctions.isEmpty()
                && subqueries.isEmpty()
                && cross.stream().allMatch(join -> join.filters().isEmpty())) {
            return;
        }
        sql.append(" WHERE ");
        int i = 0;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            if (alias != null) {
                sql.append(Identifiers.quote(alias)).append('.');
            }
            sql.append(Identifiers.quote(entry.getKey()));
            FilterAtoms.append(sql, args, entry.getKey(), entry.getValue());
        }
        for (QueryOr or : disjunctions) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            appendDisjunction(sql, alias, or, args);
        }
        for (QueryJoin join : cross) {
            for (Map.Entry<String, Object> entry : join.filters().entrySet()) {
                if (i++ > 0) {
                    sql.append(" AND ");
                }
                sql.append(Identifiers.quote(join.alias())).append('.').append(Identifiers.quote(entry.getKey()));
                FilterAtoms.append(sql, args, entry.getKey(), entry.getValue());
            }
        }
        for (QuerySubquery subquery : subqueries) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            appendSubquery(sql, alias, subquery, args);
        }
    }

    private static void appendDisjunction(StringBuilder sql, String alias, QueryOr or, List<Object> args) {
        sql.append('(');
        int b = 0;
        for (Map<String, Object> branch : or.branches()) {
            if (b++ > 0) {
                sql.append(" OR ");
            }
            boolean wrap = branch.size() > 1;
            if (wrap) {
                sql.append('(');
            }
            int c = 0;
            for (Map.Entry<String, Object> entry : branch.entrySet()) {
                if (c++ > 0) {
                    sql.append(" AND ");
                }
                if (alias != null) {
                    sql.append(Identifiers.quote(alias)).append('.');
                }
                sql.append(Identifiers.quote(entry.getKey()));
                FilterAtoms.append(sql, args, entry.getKey(), entry.getValue());
            }
            if (wrap) {
                sql.append(')');
            }
        }
        sql.append(')');
    }

    private static void appendSubquery(StringBuilder sql, String outerAlias, QuerySubquery subquery, List<Object> args) {
        if (subquery.kind() == QuerySubquery.Kind.IN) {
            appendColumn(sql, outerAlias, subquery.outerColumn());
        sql.append(" IN (SELECT ").append(Identifiers.quote(subquery.projection()))
                    .append(" FROM ").append(Identifiers.quoteTable(subquery.table()));
            boolean aliased = !subquery.alias().equals(subquery.table());
            if (aliased) {
                sql.append(' ').append(Identifiers.quote(subquery.alias()));
            }
            appendSubqueryPredicates(sql, aliased ? subquery.alias() : null, subquery, args, false);
            sql.append(')');
            return;
        }
        sql.append("EXISTS (SELECT 1 FROM ").append(Identifiers.quoteTable(subquery.table()));
        boolean aliased = !subquery.correlated().isEmpty()
                || !subquery.alias().equals(subquery.table())
                || !subquery.nested().isEmpty();
        if (aliased) {
            sql.append(' ').append(Identifiers.quote(subquery.alias()));
        }
        boolean started = false;
        for (JoinOn on : subquery.correlated()) {
            sql.append(started ? " AND " : " WHERE ");
            started = true;
            sql.append(Identifiers.quote(on.leftAlias())).append('.').append(Identifiers.quote(on.leftColumn()))
                    .append(" = ")
                    .append(Identifiers.quote(on.rightAlias())).append('.').append(Identifiers.quote(on.rightColumn()));
        }
        appendSubqueryPredicates(sql, aliased ? subquery.alias() : null, subquery, args, started);
        sql.append(')');
    }

    private static void appendSubqueryPredicates(
            StringBuilder sql,
            String filterAlias,
            QuerySubquery subquery,
            List<Object> args,
            boolean whereStarted
    ) {
        boolean started = whereStarted;
        for (Map.Entry<String, Object> entry : subquery.filters().entrySet()) {
            sql.append(started ? " AND " : " WHERE ");
            started = true;
            if (filterAlias != null) {
                sql.append(Identifiers.quote(filterAlias)).append('.');
            }
            sql.append(Identifiers.quote(entry.getKey()));
            FilterAtoms.append(sql, args, entry.getKey(), entry.getValue());
        }
        for (QuerySubquery nested : subquery.nested()) {
            sql.append(started ? " AND " : " WHERE ");
            started = true;
            appendSubquery(sql, filterAlias, nested, args);
        }
    }

    private static void appendOrderBy(StringBuilder sql, String alias, List<QuerySort> sorts) {
        if (sorts.isEmpty()) {
            return;
        }
        sql.append(" ORDER BY ");
        int i = 0;
        for (QuerySort sort : sorts) {
            if (i++ > 0) {
                sql.append(", ");
            }
            appendColumn(sql, alias, sort.column());
            sql.append(' ').append(sort.direction().name());
        }
    }

    private static void appendPage(StringBuilder sql, QueryPage page, List<Object> args) {
        if (page == null) {
            return;
        }
        sql.append(" LIMIT ? OFFSET ?");
        args.add(page.limit());
        args.add(page.offset());
    }

    private static void appendColumn(StringBuilder sql, String alias, String column) {
        int dot = column.indexOf('.');
        if (dot >= 0) {
            sql.append(Identifiers.quote(column.substring(0, dot)))
                    .append('.')
                    .append(Identifiers.quote(column.substring(dot + 1)));
            return;
        }
        if (alias != null) {
            sql.append(Identifiers.quote(alias)).append('.');
        }
        sql.append(Identifiers.quote(column));
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

    private static void placeholders(StringBuilder sql, Map<String, Object> values, List<Object> args) {
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append('?');
            args.add(FilterAtoms.bind(entry.getKey(), entry.getValue()));
        }
    }
}
