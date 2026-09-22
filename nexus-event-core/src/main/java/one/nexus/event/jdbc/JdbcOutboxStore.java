package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.event.DomainEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.event.EventPort;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.event.OutboxRecord;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExecutor;

public final class JdbcOutboxStore implements OutboxPort, EventPort {
    private final DataSource dataSource;
    private final ExecutionIsolation isolation;
    private final ExecutionSettings settings;
    private final Clock clock;

    public JdbcOutboxStore(DataSource dataSource, ExecutionIsolation isolation, ExecutionSettings settings, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.settings = settings == null ? ExecutionSettings.defaults() : settings;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public UUID append(OutboxAppendCommand command) {
        IsolatedEnvelope envelope = isolation.envelopeFromCurrent(command.destination(), command.payload());
        StoredMembership membership = isolation.requireTenantMembership();
        IsolatedExecutor.requirePayloadTenant(isolation.requireBound(), envelope.tenantId());
        UUID id = UuidV7.generate();
        Instant now = Instant.now(clock);
        Jdbc.transactionalRun(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO platform.exec_outbox (
                        id, tenant_id, trace_id, actor_id, actor_type, membership_id,
                        destination, event_type, event_version, aggregate_type, aggregate_id,
                        correlation_id, causation_id, payload, status, attempt, max_attempts,
                        available_at, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setObject(1, id);
                ps.setString(2, membership.tenantId());
                ps.setString(3, envelope.traceId());
                ps.setString(4, membership.actorId());
                ps.setString(5, membership.actorType());
                ps.setString(6, membership.membershipId());
                ps.setString(7, command.destination());
                ps.setString(8, command.eventType());
                ps.setInt(9, command.eventVersion());
                ps.setString(10, command.aggregateType());
                ps.setObject(11, command.aggregateId());
                ps.setString(12, command.correlationId());
                ps.setString(13, command.causationId());
                ps.setString(14, command.payload());
                ps.setString(15, "PENDING");
                ps.setInt(16, 0);
                ps.setInt(17, settings.maxAttempts());
                ps.setTimestamp(18, Jdbc.ts(now));
                ps.setTimestamp(19, Jdbc.ts(now));
                ps.setTimestamp(20, Jdbc.ts(now));
                ps.executeUpdate();
            }
            return null;
        });
        return id;
    }

    @Override
    public UUID publish(DomainEvent event, String destination, String payload) {
        Objects.requireNonNull(event, "event");
        var bound = isolation.requireBound();
        if (bound instanceof TenantContext tenant
                && event.tenantId() != null
                && !tenant.tenantId().equals(event.tenantId().toString())) {
            throw new IsolationException(IsolationErrorCode.CROSS_TENANT_ASYNC, bound.traceId());
        }
        return append(new OutboxAppendCommand(
                destination,
                event.eventType(),
                event.eventVersion() < 1 ? 1 : event.eventVersion(),
                payload,
                event.aggregateType(),
                event.aggregateId(),
                event.correlationId(),
                event.causationId()));
    }

    public List<OutboxRecord> leaseBatch(String workerId, int limit) {
        Instant now = Instant.now(clock);
        Instant leaseUntil = now.plus(settings.leaseDuration());
        return Jdbc.call(dataSource, connection -> {
            connection.setAutoCommit(false);
            try {
                List<UUID> ids = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT id FROM platform.exec_outbox
                        WHERE status IN ('PENDING', 'FAILED')
                          AND available_at <= ?
                          AND (lease_until IS NULL OR lease_until < ?)
                        ORDER BY available_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """)) {
                    select.setTimestamp(1, Jdbc.ts(now));
                    select.setTimestamp(2, Jdbc.ts(now));
                    select.setInt(3, limit);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getObject(1, UUID.class));
                        }
                    }
                }
                List<OutboxRecord> leased = new ArrayList<>();
                for (UUID id : ids) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE platform.exec_outbox
                            SET status = 'DISPATCHING',
                                attempt = attempt + 1,
                                lease_until = ?,
                                leased_by = ?,
                                updated_at = ?
                            WHERE id = ?
                            """)) {
                        update.setTimestamp(1, Jdbc.ts(leaseUntil));
                        update.setString(2, workerId);
                        update.setTimestamp(3, Jdbc.ts(now));
                        update.setObject(4, id);
                        update.executeUpdate();
                    }
                    load(connection, id).ifPresent(leased::add);
                }
                connection.commit();
                return leased;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public void markDispatched(UUID id) {
        Instant now = Instant.now(clock);
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_outbox
                    SET status = 'DISPATCHED', lease_until = NULL, leased_by = NULL,
                        dispatched_at = ?, updated_at = ?, last_error = NULL
                    WHERE id = ?
                    """)) {
                ps.setTimestamp(1, Jdbc.ts(now));
                ps.setTimestamp(2, Jdbc.ts(now));
                ps.setObject(3, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public boolean markRetryOrDead(OutboxRecord record, String error) {
        Instant now = Instant.now(clock);
        boolean dead = record.attempt() >= record.maxAttempts();
        Instant next = now.plus(settings.backoff(record.attempt()));
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_outbox
                    SET status = ?, last_error = ?, lease_until = NULL, leased_by = NULL,
                        available_at = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, dead ? "DEAD" : "FAILED");
                ps.setString(2, error);
                ps.setTimestamp(3, Jdbc.ts(dead ? now : next));
                ps.setTimestamp(4, Jdbc.ts(now));
                ps.setObject(5, record.id());
                ps.executeUpdate();
            }
            return null;
        });
        return dead;
    }

    public void requeue(UUID id) {
        Instant now = Instant.now(clock);
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_outbox
                    SET status = 'PENDING', lease_until = NULL, leased_by = NULL,
                        available_at = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                ps.setTimestamp(1, Jdbc.ts(now));
                ps.setTimestamp(2, Jdbc.ts(now));
                ps.setObject(3, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public int reclaimExpiredLeases() {
        Instant now = Instant.now(clock);
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_outbox
                    SET status = 'FAILED', lease_until = NULL, leased_by = NULL, updated_at = ?
                    WHERE status = 'DISPATCHING' AND lease_until IS NOT NULL AND lease_until < ?
                    """)) {
                ps.setTimestamp(1, Jdbc.ts(now));
                ps.setTimestamp(2, Jdbc.ts(now));
                return ps.executeUpdate();
            }
        });
    }

    public int republishStuck(java.time.Duration olderThan) {
        Instant cutoff = Instant.now(clock).minus(olderThan);
        Instant now = Instant.now(clock);
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_outbox
                    SET status = 'PENDING', lease_until = NULL, leased_by = NULL,
                        available_at = ?, updated_at = ?
                    WHERE status IN ('PENDING', 'FAILED', 'DISPATCHING')
                      AND created_at < ?
                      AND status <> 'DISPATCHED'
                    """)) {
                ps.setTimestamp(1, Jdbc.ts(now));
                ps.setTimestamp(2, Jdbc.ts(now));
                ps.setTimestamp(3, Jdbc.ts(cutoff));
                return ps.executeUpdate();
            }
        });
    }

    public Optional<Instant> oldestPendingCreatedAt() {
        return Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT MIN(created_at) FROM platform.exec_outbox
                    WHERE status IN ('PENDING', 'FAILED')
                    """);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Instant min = Jdbc.instant(rs.getTimestamp(1));
                return Optional.ofNullable(min);
            }
        });
    }

    public Optional<OutboxRecord> find(UUID id) {
        return Jdbc.call(dataSource, connection -> load(connection, id));
    }

    private Optional<OutboxRecord> load(java.sql.Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, tenant_id, trace_id, actor_id, actor_type, membership_id,
                       destination, event_type, event_version, aggregate_type, aggregate_id,
                       correlation_id, causation_id, payload, status, attempt, max_attempts,
                       available_at, lease_until, last_error, created_at
                FROM platform.exec_outbox WHERE id = ?
                """)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    static OutboxRecord map(ResultSet rs) throws SQLException {
        return new OutboxRecord(
                rs.getObject("id", UUID.class),
                new StoredMembership(
                        rs.getString("tenant_id"),
                        rs.getString("actor_id"),
                        rs.getString("actor_type"),
                        rs.getString("membership_id")),
                rs.getString("trace_id"),
                rs.getString("destination"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("attempt"),
                rs.getInt("max_attempts"),
                Jdbc.instant(rs.getTimestamp("available_at")),
                Jdbc.instant(rs.getTimestamp("lease_until")),
                rs.getString("last_error"),
                Jdbc.instant(rs.getTimestamp("created_at")));
    }
}
