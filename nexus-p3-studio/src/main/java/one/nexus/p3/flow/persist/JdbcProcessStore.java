package one.nexus.p3.flow.persist;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.ProcessStatus;
import one.nexus.p3.flow.TodoStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Platform process metadata. Queries always include tenant_id. Never reads Flowable act_* tables.
 */
public final class JdbcProcessStore {
    private final JdbcTemplate jdbc;

    public JdbcProcessStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProcessInstanceView insertInstance(ProcessInstanceView instance) {
        jdbc.update(
                """
                INSERT INTO business_meta.process_instance (
                    id, tenant_id, process_key, business_key, initiator_person_id,
                    assignee_type, assignee_ref, flowable_instance_id, status, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                instance.id(),
                instance.tenantId(),
                instance.processKey(),
                instance.businessKey(),
                instance.initiatorPersonId(),
                instance.assigneeType().name(),
                instance.assigneeRef(),
                instance.flowableInstanceId(),
                instance.status().name(),
                sqlTimestamp(instance.startedAt()),
                sqlTimestamp(instance.endedAt()));
        return instance;
    }

    public TodoView insertTodo(TodoView todo) {
        jdbc.update(
                """
                INSERT INTO business_meta.process_todo (
                    id, tenant_id, instance_id, flowable_task_id, task_name, assignee_person_id,
                    candidate_person_ids, status, outcome, acted_by_person_id, acted_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                todo.id(),
                todo.tenantId(),
                todo.instanceId(),
                todo.flowableTaskId(),
                todo.taskName(),
                todo.assigneePersonId(),
                joinIds(todo.candidatePersonIds()),
                todo.status().name(),
                todo.outcome(),
                todo.actedByPersonId(),
                sqlTimestamp(todo.actedAt()),
                sqlTimestamp(todo.createdAt()));
        return todo;
    }

    public void recordAction(UUID tenantId, UUID instanceId, UUID todoId, UUID actorId, String action, String key, Instant at) {
        jdbc.update(
                """
                INSERT INTO business_meta.process_action (
                    id, tenant_id, instance_id, todo_id, actor_person_id, action, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UuidV7.generate(), tenantId, instanceId, todoId, actorId, action, key, sqlTimestamp(at));
    }

    public List<TodoView> todosForInstance(UUID tenantId, UUID instanceId) {
        return jdbc.query(
                """
                SELECT t.*, i.business_key, i.status AS instance_status
                FROM business_meta.process_todo t
                JOIN business_meta.process_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
                WHERE t.tenant_id = ? AND t.instance_id = ?
                ORDER BY t.created_at
                """,
                todoMapper(), tenantId, instanceId);
    }

    public Optional<ProcessInstanceView> findInstance(UUID tenantId, UUID instanceId) {
        List<ProcessInstanceView> rows = jdbc.query(
                "SELECT * FROM business_meta.process_instance WHERE tenant_id = ? AND id = ?",
                instanceMapper(), tenantId, instanceId);
        return rows.stream().findFirst();
    }

    public Optional<ProcessInstanceView> findInstanceByBusinessKey(UUID tenantId, String businessKey) {
        List<ProcessInstanceView> rows = jdbc.query(
                "SELECT * FROM business_meta.process_instance WHERE tenant_id = ? AND business_key = ?",
                instanceMapper(), tenantId, businessKey);
        return rows.stream().findFirst();
    }

    public Optional<TodoView> findTodo(UUID tenantId, UUID todoId) {
        List<TodoView> rows = jdbc.query(
                """
                SELECT t.*, i.business_key, i.status AS instance_status
                FROM business_meta.process_todo t
                JOIN business_meta.process_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
                WHERE t.tenant_id = ? AND t.id = ?
                """,
                todoMapper(), tenantId, todoId);
        return rows.stream().findFirst();
    }

    public List<TodoView> pendingFor(UUID tenantId, UUID personId) {
        return jdbc.query(
                """
                SELECT t.*, i.business_key, i.status AS instance_status
                FROM business_meta.process_todo t
                JOIN business_meta.process_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
                WHERE t.tenant_id = ? AND t.status = 'PENDING'
                  AND (t.assignee_person_id = ? OR t.candidate_person_ids = ? OR t.candidate_person_ids LIKE ?
                       OR t.candidate_person_ids LIKE ? OR t.candidate_person_ids LIKE ?)
                ORDER BY t.created_at DESC
                """,
                todoMapper(),
                tenantId,
                personId,
                personId.toString(),
                personId + ",%",
                "%," + personId + ",%",
                "%," + personId);
    }

    public List<TodoView> doneBy(UUID tenantId, UUID personId) {
        return jdbc.query(
                """
                SELECT t.*, i.business_key, i.status AS instance_status
                FROM business_meta.process_todo t
                JOIN business_meta.process_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
                WHERE t.tenant_id = ? AND t.status = 'DONE' AND t.acted_by_person_id = ?
                ORDER BY t.acted_at DESC
                """,
                todoMapper(), tenantId, personId);
    }

    public List<ProcessInstanceView> initiatedBy(UUID tenantId, UUID personId) {
        return jdbc.query(
                """
                SELECT * FROM business_meta.process_instance
                WHERE tenant_id = ? AND initiator_person_id = ?
                ORDER BY started_at DESC
                """,
                instanceMapper(), tenantId, personId);
    }

    public void completeTodo(UUID tenantId, UUID todoId, UUID actorId, String outcome, Instant at) {
        jdbc.update(
                """
                UPDATE business_meta.process_todo
                SET status = 'DONE', outcome = ?, acted_by_person_id = ?, acted_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                outcome, actorId, sqlTimestamp(at), tenantId, todoId);
    }

    public void cancelPending(UUID tenantId, UUID instanceId) {
        jdbc.update(
                """
                UPDATE business_meta.process_todo
                SET status = 'CANCELLED'
                WHERE tenant_id = ? AND instance_id = ? AND status = 'PENDING'
                """,
                tenantId, instanceId);
    }

    public void finishInstance(UUID tenantId, UUID instanceId, ProcessStatus status, Instant at) {
        jdbc.update(
                """
                UPDATE business_meta.process_instance
                SET status = ?, ended_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                status.name(), sqlTimestamp(at), tenantId, instanceId);
    }

    public boolean actionExists(UUID tenantId, String idempotencyKey) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM business_meta.process_action WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, tenantId, idempotencyKey);
        return count != null && count > 0;
    }

    private static RowMapper<ProcessInstanceView> instanceMapper() {
        return (rs, rowNum) -> new ProcessInstanceView(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("process_key"),
                rs.getString("business_key"),
                rs.getObject("initiator_person_id", UUID.class),
                AssigneeType.valueOf(rs.getString("assignee_type")),
                rs.getString("assignee_ref"),
                rs.getString("flowable_instance_id"),
                ProcessStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant());
    }

    private static RowMapper<TodoView> todoMapper() {
        return (rs, rowNum) -> new TodoView(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("instance_id", UUID.class),
                rs.getString("flowable_task_id"),
                rs.getString("task_name"),
                rs.getObject("assignee_person_id", UUID.class),
                splitIds(rs.getString("candidate_person_ids")),
                TodoStatus.valueOf(rs.getString("status")),
                rs.getString("outcome"),
                rs.getObject("acted_by_person_id", UUID.class),
                rs.getTimestamp("acted_at") == null ? null : rs.getTimestamp("acted_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("business_key"),
                ProcessStatus.valueOf(rs.getString("instance_status")),
                null);
    }

    static String joinIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (UUID id : ids) {
            parts.add(id.toString());
        }
        return String.join(",", parts);
    }

    static List<UUID> splitIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
    }
}
