package one.nexus.event.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

public final class Jdbc {
    @FunctionalInterface
    public interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private Jdbc() {}

    public static <T> T call(DataSource dataSource, ConnectionWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Uses the Spring-transaction connection when one is bound so Outbox append
     * commits or rolls back with the caller. Without an active transaction this
     * obtains and releases a connection the same way as a plain JDBC call.
     */
    public static <T> T transactionalCall(DataSource dataSource, ConnectionWork<T> work) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return work.run(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    public static void run(DataSource dataSource, ConnectionWork<Void> work) {
        call(dataSource, work);
    }

    public static void transactionalRun(DataSource dataSource, ConnectionWork<Void> work) {
        transactionalCall(dataSource, work);
    }

    public static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static <T> List<T> list(PreparedStatement statement, RowMapper<T> mapper) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        }
    }
}
