package one.nexus.task.jdbc;

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
import one.nexus.common.id.UuidV7;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.event.support.Jdbc;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExecutor;
import one.nexus.task.TaskQueue;
import one.nexus.task.TaskRecord;
import one.nexus.task.TaskSubmitGuard;

public final class JdbcTaskQueue implements TaskQueue {
    private final DataSource dataSource;
    private final ExecutionIsolation isolation;
    private final ExecutionSettings settings;
    private final Clock clock;
    private final TaskSubmitGuard submitGuard;

    public JdbcTaskQueue(DataSource dataSource, ExecutionIsolation isolation, ExecutionSettings settings, Clock clock) {
        this(dataSource, isolation, settings, clock, null);
    }

    public JdbcTaskQueue(
            DataSource dataSource,
            ExecutionIsolation isolation,
            ExecutionSettings settings,
            Clock clock,
            TaskSubmitGuard submitGuard
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.settings = settings == null ? ExecutionSettings.defaults() : settings;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.submitGuard = submitGuard;
    }

    @Override
    public UUID submit(String taskType, String payload) {
        return submitAt(taskType, payload, Instant.now(clock));
    }

    @Override
    public UUID submitAt(String taskType, String payload, Instant availableAt) {
        if (submitGuard != null) {
            submitGuard.assertTask(taskType);
        }
        IsolatedEnvelope envelope = isolation.envelopeFromCurrent(taskType, payload);
        StoredMembership membership = isolation.requireTenantMembership();
        IsolatedExecutor.requirePayloadTenant(isolation.requireBound(), envelope.tenantId());
        UUID id = UuidV7.generate();
        Instant now = Instant.now(clock);
        Instant when = availableAt == null ? now : availableAt;
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO platform.exec_task (
                        id, tenant_id, trace_id, actor_id, actor_type, membership_id,
                        task_type, payload, status, attempt, max_attempts,
                        available_at, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setObject(1, id);
                ps.setString(2, membership.tenantId());
                ps.setString(3, envelope.traceId());
                ps.setString(4, membership.actorId());
                ps.setString(5, membership.actorType());
                ps.setString(6, membership.membershipId());
                ps.setString(7, taskType);
                ps.setString(8, payload);
                ps.setString(9, "PENDING");
                ps.setInt(10, 0);
                ps.setInt(11, settings.maxAttempts());
                ps.setTimestamp(12, Jdbc.ts(when));
                ps.setTimestamp(13, Jdbc.ts(now));
                ps.setTimestamp(14, Jdbc.ts(now));
                ps.executeUpdate();
            }
            return null;
        });
        return id;
    }

    public List<TaskRecord> leaseBatch(String workerId, int limit) {
        Instant now = Instant.now(clock);
        Instant leaseUntil = now.plus(settings.leaseDuration());
        return Jdbc.call(dataSource, connection -> {
            connection.setAutoCommit(false);
            try {
                List<UUID> ids = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT id FROM platform.exec_task
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
                List<TaskRecord> leased = new ArrayList<>();
                for (UUID id : ids) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE platform.exec_task
                            SET status = 'LEASED', attempt = attempt + 1,
                                lease_until = ?, leased_by = ?, updated_at = ?
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

    public void markSucceeded(UUID id) {
        Instant now = Instant.now(clock);
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_task
                    SET status = 'SUCCEEDED', lease_until = NULL, leased_by = NULL,
                        completed_at = ?, updated_at = ?, last_error = NULL
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

    public boolean markRetryOrDead(TaskRecord record, String error) {
        Instant now = Instant.now(clock);
        boolean dead = record.attempt() >= record.maxAttempts();
        Instant next = now.plus(settings.backoff(record.attempt()));
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_task
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

    public Optional<TaskRecord> find(UUID id) {
        return Jdbc.call(dataSource, connection -> load(connection, id));
    }

    private Optional<TaskRecord> load(java.sql.Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, tenant_id, trace_id, actor_id, actor_type, membership_id,
                       task_type, payload, status, attempt, max_attempts,
                       available_at, lease_until, last_error, created_at
                FROM platform.exec_task WHERE id = ?
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

    static TaskRecord map(ResultSet rs) throws SQLException {
        return new TaskRecord(
                rs.getObject("id", UUID.class),
                new StoredMembership(
                        rs.getString("tenant_id"),
                        rs.getString("actor_id"),
                        rs.getString("actor_type"),
                        rs.getString("membership_id")),
                rs.getString("trace_id"),
                rs.getString("task_type"),
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
