package one.nexus.p3.flow.persist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.p3.flow.ProcessDefinitionDraft;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "nexus.flow.engine")
public class JdbcProcessDefinitionDraftStore {
    private static final RowMapper<ProcessDefinitionDraft> ROW = (rs, row) -> new ProcessDefinitionDraft(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("process_key"),
            rs.getString("name"),
            rs.getString("bpmn_xml"),
            rs.getInt("version"),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcProcessDefinitionDraftStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProcessDefinitionDraft> list(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM business_meta.process_definition_draft WHERE tenant_id = ? ORDER BY updated_at DESC, process_key",
                ROW,
                tenantId);
    }

    public Optional<ProcessDefinitionDraft> find(UUID tenantId, String key) {
        return jdbc.query(
                        "SELECT * FROM business_meta.process_definition_draft WHERE tenant_id = ? AND process_key = ?",
                        ROW,
                        tenantId,
                        key)
                .stream()
                .findFirst();
    }

    public ProcessDefinitionDraft save(UUID tenantId, UUID actorId, String key, String name, String bpmnXml) {
        jdbc.update(
                """
                INSERT INTO business_meta.process_definition_draft (
                    id, tenant_id, process_key, name, bpmn_xml, version, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, now(), now())
                ON CONFLICT (tenant_id, process_key) DO UPDATE SET
                    name = EXCLUDED.name,
                    bpmn_xml = EXCLUDED.bpmn_xml,
                    version = business_meta.process_definition_draft.version + 1,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                """,
                UuidV7.generate(),
                tenantId,
                key,
                name,
                bpmnXml,
                actorId);
        return find(tenantId, key).orElseThrow(() -> new IllegalStateException("saved process draft not found"));
    }
}
