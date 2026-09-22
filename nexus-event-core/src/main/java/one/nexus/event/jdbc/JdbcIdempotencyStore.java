package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.support.Jdbc;

public final class JdbcIdempotencyStore implements IdempotencyStore {
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcIdempotencyStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public Claim claim(String scope, String key, String tenantId, String traceId) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("scope and key are required");
        }
        Instant now = Instant.now(clock);
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO platform.exec_idempotency (scope, idempotency_key, tenant_id, trace_id, first_seen_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (scope, idempotency_key) DO NOTHING
                    """)) {
                ps.setString(1, scope);
                ps.setString(2, key);
                ps.setString(3, tenantId);
                ps.setString(4, traceId);
                ps.setTimestamp(5, Jdbc.ts(now));
                int inserted = ps.executeUpdate();
                return inserted == 1 ? Claim.FIRST : Claim.DUPLICATE;
            } catch (SQLException ex) {
                throw ex;
            }
        });
    }

    @Override
    public boolean exists(String scope, String key) {
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT 1 FROM platform.exec_idempotency
                    WHERE scope = ? AND idempotency_key = ?
                    """)) {
                ps.setString(1, scope);
                ps.setString(2, key);
                try (var rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }
}
