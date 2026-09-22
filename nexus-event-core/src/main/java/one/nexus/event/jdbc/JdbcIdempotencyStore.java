package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
        return insertClaim(scope, key, tenantId, traceId, false);
    }

    @Override
    public Claim claimInTransaction(String scope, String key, String tenantId, String traceId) {
        return insertClaim(scope, key, tenantId, traceId, true);
    }

    private Claim insertClaim(String scope, String key, String tenantId, String traceId, boolean transactional) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("scope and key are required");
        }
        Instant now = Instant.now(clock);
        Jdbc.ConnectionWork<Claim> work = connection -> {
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
            }
        };
        return transactional ? Jdbc.transactionalCall(dataSource, work) : Jdbc.call(dataSource, work);
    }

    @Override
    public void remember(String scope, String key, String resultRef) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("scope and key are required");
        }
        if (resultRef == null || resultRef.isBlank()) {
            throw new IllegalArgumentException("resultRef is required");
        }
        Jdbc.transactionalCall(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_idempotency
                       SET result_ref = ?
                     WHERE scope = ? AND idempotency_key = ?
                    """)) {
                ps.setString(1, resultRef);
                ps.setString(2, scope);
                ps.setString(3, key);
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("idempotency key not claimed: " + scope + "/" + key);
                }
                return null;
            }
        });
    }

    @Override
    public Optional<String> resultRef(String scope, String key) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("scope and key are required");
        }
        return Jdbc.transactionalCall(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT result_ref FROM platform.exec_idempotency
                    WHERE scope = ? AND idempotency_key = ?
                    """)) {
                ps.setString(1, scope);
                ps.setString(2, key);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String ref = rs.getString(1);
                    return ref == null || ref.isBlank() ? Optional.empty() : Optional.of(ref);
                }
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
