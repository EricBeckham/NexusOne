package one.nexus.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoSuite;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * INSERT-only copy of aged {@code platform.audit_record} rows into
 * {@code platform.audit_record_archive}. Never issues UPDATE/DELETE on the hot
 * table. Re-runs are idempotent on {@code event_id}.
 */
public final class JdbcAuditArchivePort implements AuditArchivePort {
    private static final String COPY =
            """
            INSERT INTO platform.audit_record_archive (
              chain_index, event_id, event_version, occurred_at, category, event_type,
              tenant_id, actor_subject_id, actor_tenant_id, platform_context, reason,
              trace_id, correlation_id, causation_id, outcome, resource_type, resource_id,
              details, previous_hash, event_hash, cipher_suite, digest_key_version, archived_at)
            SELECT
              chain_index, event_id, event_version, occurred_at, category, event_type,
              tenant_id, actor_subject_id, actor_tenant_id, platform_context, reason,
              trace_id, correlation_id, causation_id, outcome, resource_type, resource_id,
              details, previous_hash, event_hash, cipher_suite, digest_key_version, ?
            FROM platform.audit_record
            WHERE occurred_at < ?
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final DataSource dataSource;

    public JdbcAuditArchivePort(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ArchiveRunResult copyHotOlderThan(Duration hotRetention, Instant now) {
        Objects.requireNonNull(hotRetention, "hotRetention");
        Objects.requireNonNull(now, "now");
        Instant cutoff = now.minus(hotRetention);
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            boolean own = !DataSourceUtils.isConnectionTransactional(connection, dataSource);
            boolean previous = connection.getAutoCommit();
            if (own) {
                connection.setAutoCommit(false);
            }
            try {
                int copied;
                try (PreparedStatement statement = connection.prepareStatement(COPY)) {
                    statement.setObject(1, now.atOffset(ZoneOffset.UTC));
                    statement.setObject(2, cutoff.atOffset(ZoneOffset.UTC));
                    copied = statement.executeUpdate();
                }
                int archiveSize = countArchive(connection);
                if (own) {
                    connection.commit();
                }
                return ArchiveRunResult.copied(copied, archiveSize, cutoff);
            } catch (RuntimeException | SQLException ex) {
                if (own) {
                    connection.rollback();
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("audit archive copy failed", ex);
            } finally {
                if (own) {
                    connection.setAutoCommit(previous);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("audit archive copy failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public List<ChainedAuditEvent> findArchivedByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM platform.audit_record_archive WHERE trace_id = ? ORDER BY chain_index")) {
            statement.setString(1, traceId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ChainedAuditEvent> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return List.copyOf(out);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("audit archive query failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static int countArchive(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                        connection.prepareStatement("SELECT count(*) FROM platform.audit_record_archive");
                ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static ChainedAuditEvent map(ResultSet rs) throws SQLException {
        OffsetDateTime occurred = rs.getObject("occurred_at", OffsetDateTime.class);
        AuditEvent event = new AuditEvent(
                rs.getObject("event_id", UUID.class),
                rs.getInt("event_version"),
                occurred.toInstant(),
                AuditCategory.valueOf(rs.getString("category")),
                rs.getString("event_type"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("actor_subject_id", UUID.class),
                rs.getObject("actor_tenant_id", UUID.class),
                rs.getBoolean("platform_context"),
                rs.getString("reason"),
                rs.getString("trace_id"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("outcome"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                CanonicalAudit.parseDetailsJson(rs.getString("details")));
        return new ChainedAuditEvent(
                rs.getLong("chain_index"),
                event,
                rs.getBytes("previous_hash"),
                rs.getBytes("event_hash"),
                CryptoSuite.valueOf(rs.getString("cipher_suite")),
                rs.getString("digest_key_version"));
    }
}
