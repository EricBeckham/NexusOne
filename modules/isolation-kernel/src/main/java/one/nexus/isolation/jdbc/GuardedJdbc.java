package one.nexus.isolation.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.QueryCompiler;

/**
 * Only public JDBC path for tenant tables. Compiles (and therefore guards) before preparing a statement.
 */
public final class GuardedJdbc {
    private final QueryCompiler compiler;

    public GuardedJdbc(QueryCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    public RenderedSql compile(QueryAst query, AccessContext context) {
        return compiler.compile(query, context);
    }

    public int executeUpdate(Connection connection, QueryAst query, AccessContext context) throws SQLException {
        RenderedSql sql = compiler.compile(query, context);
        return executeUpdate(connection, sql);
    }

    public int executeUpdate(Connection connection, QueryAst query) throws SQLException {
        return executeUpdate(connection, query, AccessContextHolder.current());
    }

    public List<Map<String, Object>> executeQuery(Connection connection, QueryAst query, AccessContext context)
            throws SQLException {
        RenderedSql sql = compiler.compile(query, context);
        return executeQuery(connection, sql);
    }

    public List<Map<String, Object>> executeQuery(Connection connection, QueryAst query) throws SQLException {
        return executeQuery(connection, query, AccessContextHolder.current());
    }

    public int executeUpdate(Connection connection, RenderedSql sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.sql())) {
            bind(statement, sql);
            return statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> executeQuery(Connection connection, RenderedSql sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.sql())) {
            bind(statement, sql);
            try (ResultSet results = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int columns = results.getMetaData().getColumnCount();
                while (results.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(results.getMetaData().getColumnLabel(i), results.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private static void bind(PreparedStatement statement, RenderedSql sql) throws SQLException {
        for (int i = 0; i < sql.parameters().size(); i++) {
            statement.setObject(i + 1, sql.parameters().get(i));
        }
    }
}
