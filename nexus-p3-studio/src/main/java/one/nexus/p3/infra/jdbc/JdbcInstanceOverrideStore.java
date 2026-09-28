package one.nexus.p3.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.InstanceOverrideStore;
import one.nexus.p3.instance.ObjectType;
import one.nexus.p3.instance.OverrideRecord;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PLX1 C6. Every statement is scoped by {@code tenant_id}. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcInstanceOverrideStore implements InstanceOverrideStore {
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcInstanceOverrideStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OverrideRecord> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        List<OverrideRecord> rows = jdbc.query("""
                SELECT tenant_id, instance_id, object_type, object_code, base_version, content::text AS content,
                       updated_at, updated_by
                  FROM business_meta.instance_object_override
                 WHERE tenant_id = ? AND instance_id = ? AND object_type = ? AND object_code = ?
                """, (rs, i) -> map(rs), tenantId, instanceId, type.name(), code);
        return rows.stream().findFirst();
    }

    @Override
    public List<OverrideRecord> list(UUID tenantId) {
        return jdbc.query("""
                SELECT tenant_id, instance_id, object_type, object_code, base_version, content::text AS content,
                       updated_at, updated_by
                  FROM business_meta.instance_object_override
                 WHERE tenant_id = ?
                 ORDER BY instance_id, object_type, object_code
                """, (rs, i) -> map(rs), tenantId);
    }

    @Override
    public OverrideRecord save(OverrideRecord record) {
        jdbc.update("""
                INSERT INTO business_meta.instance_object_override
                    (tenant_id, instance_id, object_type, object_code, base_version, content, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (tenant_id, instance_id, object_type, object_code)
                DO UPDATE SET base_version = EXCLUDED.base_version,
                              content = EXCLUDED.content,
                              updated_at = EXCLUDED.updated_at,
                              updated_by = EXCLUDED.updated_by
                """,
                record.tenantId(), record.instanceId(), record.objectType().name(), record.objectCode(),
                record.baseVersion(), json(record.content()), sqlTimestamp(record.updatedAt()), record.updatedBy());
        return find(record.tenantId(), record.instanceId(), record.objectType(), record.objectCode()).orElseThrow();
    }

    @Override
    public void delete(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        jdbc.update("""
                DELETE FROM business_meta.instance_object_override
                 WHERE tenant_id = ? AND instance_id = ? AND object_type = ? AND object_code = ?
                """, tenantId, instanceId, type.name(), code);
    }

    private OverrideRecord map(ResultSet rs) throws SQLException {
        return new OverrideRecord(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("instance_id", UUID.class),
                ObjectType.valueOf(rs.getString("object_type")),
                rs.getString("object_code"),
                rs.getString("base_version"),
                read(rs.getString("content")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by"));
    }

    private String json(Map<String, Object> content) {
        return mapper.writeValueAsString(content == null ? Map.of() : content);
    }

    private Map<String, Object> read(String json) {
        return mapper.readValue(json, OBJECT);
    }
}
