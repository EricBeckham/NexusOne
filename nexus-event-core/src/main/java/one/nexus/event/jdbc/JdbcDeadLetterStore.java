package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.id.UuidV7;
import one.nexus.event.DeadLetterStore;
import one.nexus.event.support.Jdbc;

public final class JdbcDeadLetterStore implements DeadLetterStore {
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcDeadLetterStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void record(String channel, UUID sourceId, String tenantId, String traceId,
                       String payloadType, String payload, String lastError, int attempt) {
        Instant now = Instant.now(clock);
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO platform.exec_dead_letter (
                        id, channel, source_id, tenant_id, trace_id, payload_type, payload, last_error, attempt, failed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setObject(1, UuidV7.generate());
                ps.setString(2, channel);
                ps.setObject(3, sourceId);
                ps.setString(4, tenantId);
                ps.setString(5, traceId);
                ps.setString(6, payloadType);
                ps.setString(7, payload);
                ps.setString(8, lastError);
                ps.setInt(9, attempt);
                ps.setTimestamp(10, Jdbc.ts(now));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<DeadLetter> list(String channel, int limit) {
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, channel, source_id, tenant_id, trace_id, payload_type, payload, last_error, attempt
                    FROM platform.exec_dead_letter
                    WHERE channel = ?
                    ORDER BY failed_at DESC
                    LIMIT ?
                    """)) {
                ps.setString(1, channel);
                ps.setInt(2, limit);
                return Jdbc.list(ps, rs -> new DeadLetter(
                        rs.getObject("id", UUID.class),
                        rs.getString("channel"),
                        rs.getObject("source_id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getString("trace_id"),
                        rs.getString("payload_type"),
                        rs.getString("payload"),
                        rs.getString("last_error"),
                        rs.getInt("attempt")));
            }
        });
    }

    public java.util.Optional<DeadLetter> find(UUID id) {
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, channel, source_id, tenant_id, trace_id, payload_type, payload, last_error, attempt
                    FROM platform.exec_dead_letter WHERE id = ?
                    """)) {
                ps.setObject(1, id);
                var rows = Jdbc.list(ps, rs -> new DeadLetter(
                        rs.getObject("id", UUID.class),
                        rs.getString("channel"),
                        rs.getObject("source_id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getString("trace_id"),
                        rs.getString("payload_type"),
                        rs.getString("payload"),
                        rs.getString("last_error"),
                        rs.getInt("attempt")));
                return rows.stream().findFirst();
            }
        });
    }
}
