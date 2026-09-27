package one.nexus.p3.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.completion.CompletionAction;
import one.nexus.p3.completion.CompletionActionStore;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** FA3: JDBC adapter for {@code business_meta.completion_action} (V710); every statement carries {@code tenant_id}. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcCompletionActionStore implements CompletionActionStore {
    private static final String COLUMNS = """
            id, tenant_id, name, source_ledger_code, process_key, trigger_outcome, action_type, target_ledger_code,
            field_mapping::text AS field_mapping, enabled, version, created_at, created_by, updated_at, updated_by
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcCompletionActionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CompletionAction action) {
        jdbc.update("""
                INSERT INTO business_meta.completion_action
                    (id, tenant_id, name, source_ledger_code, process_key, trigger_outcome, action_type,
                     target_ledger_code, field_mapping, enabled, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                """,
                action.id(), action.tenantId(), action.name(), action.sourceLedgerCode(), action.processKey(),
                action.trigger().name(), action.actionType().name(), action.targetLedgerCode(),
                mappingJson(action.fieldMapping()), action.enabled(), action.version(),
                sqlTimestamp(action.createdAt()), action.createdBy(), sqlTimestamp(action.updatedAt()), action.updatedBy());
    }

    @Override
    public boolean update(CompletionAction action, long expectedVersion) {
        return jdbc.update("""
                UPDATE business_meta.completion_action
                   SET name = ?, source_ledger_code = ?, process_key = ?, trigger_outcome = ?, action_type = ?,
                       target_ledger_code = ?, field_mapping = CAST(? AS jsonb), enabled = ?, version = ?,
                       updated_at = ?, updated_by = ?
                 WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                action.name(), action.sourceLedgerCode(), action.processKey(), action.trigger().name(),
                action.actionType().name(), action.targetLedgerCode(), mappingJson(action.fieldMapping()),
                action.enabled(), action.version(), sqlTimestamp(action.updatedAt()), action.updatedBy(),
                action.id(), action.tenantId(), expectedVersion) == 1;
    }

    @Override
    public Optional<CompletionAction> find(UUID tenantId, UUID id) {
        List<CompletionAction> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM business_meta.completion_action WHERE id = ? AND tenant_id = ?",
                (rs, i) -> map(rs), id, tenantId);
        return rows.stream().findFirst();
    }

    @Override
    public List<CompletionAction> list(UUID tenantId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM business_meta.completion_action WHERE tenant_id = ? ORDER BY created_at DESC",
                (rs, i) -> map(rs), tenantId);
    }

    @Override
    public List<CompletionAction> enabledFor(UUID tenantId, String sourceLedgerCode, String processKey) {
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM business_meta.completion_action
                WHERE tenant_id = ? AND source_ledger_code = ? AND process_key = ? AND enabled
                ORDER BY created_at
                """, (rs, i) -> map(rs), tenantId, sourceLedgerCode, processKey);
    }

    private CompletionAction map(ResultSet rs) throws SQLException {
        return new CompletionAction(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("name"),
                rs.getString("source_ledger_code"),
                rs.getString("process_key"),
                CompletionAction.Trigger.valueOf(rs.getString("trigger_outcome")),
                CompletionAction.ActionType.valueOf(rs.getString("action_type")),
                rs.getString("target_ledger_code"),
                mapping(rs.getString("field_mapping")),
                rs.getBoolean("enabled"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by"));
    }

    private String mappingJson(List<CompletionAction.FieldMapping> mapping) {
        List<Map<String, String>> items = new ArrayList<>();
        for (CompletionAction.FieldMapping item : mapping) {
            items.add(Map.of("target", item.target(), "kind", item.kind().name(),
                    "value", item.value() == null ? "" : item.value()));
        }
        return mapper.writeValueAsString(items);
    }

    private List<CompletionAction.FieldMapping> mapping(String json) {
        List<Map<String, String>> items = mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        List<CompletionAction.FieldMapping> out = new ArrayList<>();
        for (Map<String, String> item : items) {
            out.add(new CompletionAction.FieldMapping(item.get("target"),
                    CompletionAction.SourceKind.valueOf(item.get("kind")), item.get("value")));
        }
        return out;
    }
}
