package one.nexus.p3.infra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

/** Pins ledger JDBC to {@code business_data} so Query AST can use unqualified table names. */
public final class SearchPathConnections {
    private SearchPathConnections() {}

    public static Connection open(DataSource dataSource) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL search_path TO business_data, business_meta, flowable, platform, public");
        } catch (SQLException ex) {
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw new IllegalStateException("failed to set search_path", ex);
        }
        return connection;
    }

    public static void release(DataSource dataSource, Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
