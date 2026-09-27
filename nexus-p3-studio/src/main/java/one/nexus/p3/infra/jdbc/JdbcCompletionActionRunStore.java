package one.nexus.p3.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.completion.CompletionActionRun;
import one.nexus.p3.completion.CompletionActionRunStore;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** FA4: JDBC adapter for {@code business_meta.completion_action_run} (V710). The unique key makes claims race-safe. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcCompletionActionRunStore implements CompletionActionRunStore {
    private static final String COLUMNS = """
            id, tenant_id, action_id, source_ledger_code, source_record_id, process_instance_id, outcome, status,
            target_record_id, attempts, error_code, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcCompletionActionRunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CompletionActionRun claim(CompletionActionRun candidate) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO business_meta.completion_action_run
                    (id, tenant_id, action_id, source_ledger_code, source_record_id, process_instance_id, outcome,
                     status, attempts, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (tenant_id, action_id, source_record_id) DO NOTHING
                """,
                candidate.id(), candidate.tenantId(), candidate.actionId(), candidate.sourceLedgerCode(),
                candidate.sourceRecordId(), candidate.processInstanceId(), candidate.outcome(), now, now);
        jdbc.update("""
                UPDATE business_meta.completion_action_run
                   SET attempts = attempts + 1, updated_at = ?
                 WHERE tenant_id = ? AND action_id = ? AND source_record_id = ? AND status <> 'DONE'
                """, now, candidate.tenantId(), candidate.actionId(), candidate.sourceRecordId());
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM business_meta.completion_action_run WHERE tenant_id = ? AND action_id = ? AND source_record_id = ?",
                (rs, i) -> map(rs), candidate.tenantId(), candidate.actionId(), candidate.sourceRecordId())
                .getFirst();
    }

    @Override
    public void markDone(UUID tenantId, UUID runId, UUID targetRecordId) {
        jdbc.update("""
                UPDATE business_meta.completion_action_run
                   SET status = 'DONE', target_record_id = ?, error_code = NULL, updated_at = ?
                 WHERE id = ? AND tenant_id = ?
                """, targetRecordId, Timestamp.from(Instant.now()), runId, tenantId);
    }

    @Override
    public void markFailed(UUID tenantId, UUID runId, String errorCode) {
        jdbc.update("""
                UPDATE business_meta.completion_action_run
                   SET status = 'FAILED', error_code = ?, updated_at = ?
                 WHERE id = ? AND tenant_id = ? AND status <> 'DONE'
                """, errorCode, Timestamp.from(Instant.now()), runId, tenantId);
    }

    @Override
    public Optional<CompletionActionRun> find(UUID tenantId, UUID runId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM business_meta.completion_action_run WHERE id = ? AND tenant_id = ?",
                (rs, i) -> map(rs), runId, tenantId).stream().findFirst();
    }

    @Override
    public List<CompletionActionRun> list(UUID tenantId, UUID actionId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 200));
        if (actionId == null) {
            return jdbc.query("SELECT " + COLUMNS
                            + " FROM business_meta.completion_action_run WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                    (rs, i) -> map(rs), tenantId, bounded);
        }
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM business_meta.completion_action_run
                WHERE tenant_id = ? AND action_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, (rs, i) -> map(rs), tenantId, actionId, bounded);
    }

    private static CompletionActionRun map(ResultSet rs) throws SQLException {
        return new CompletionActionRun(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("source_ledger_code"),
                rs.getObject("source_record_id", UUID.class),
                rs.getObject("process_instance_id", UUID.class),
                rs.getString("outcome"),
                CompletionActionRun.Status.valueOf(rs.getString("status")),
                rs.getObject("target_record_id", UUID.class),
                rs.getInt("attempts"),
                rs.getString("error_code"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
