package one.nexus.p3.infra.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.InstanceConsumptionStore;
import one.nexus.p3.instance.InstanceSnapshot;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PLX1 C4. Every statement is scoped by {@code tenant_id}. Payload field values are not written here. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcInstanceConsumptionStore implements InstanceConsumptionStore {
    private static final TypeReference<List<String>> KEYS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcInstanceConsumptionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean seen(UUID tenantId, UUID eventId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM business_meta.app_instance_event
                 WHERE tenant_id = ? AND event_id = ?
                """, Integer.class, tenantId, eventId);
        return count != null && count > 0;
    }

    @Override
    public void markSeen(UUID tenantId, UUID instanceId, UUID eventId) {
        jdbc.update("""
                INSERT INTO business_meta.app_instance_event (tenant_id, event_id, instance_id)
                VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, event_id) DO NOTHING
                """, tenantId, eventId, instanceId);
    }

    @Override
    public Optional<InstanceSnapshot> find(UUID tenantId, UUID instanceId) {
        List<InstanceSnapshot> rows = jdbc.query("""
                SELECT instance_id, instance_revision, product_code, app_code, version, previous_version, mode,
                       entries::text AS entries, status
                  FROM business_meta.app_instance_state
                 WHERE tenant_id = ? AND instance_id = ?
                """, (rs, i) -> new InstanceSnapshot(
                rs.getObject("instance_id", UUID.class),
                rs.getLong("instance_revision"),
                rs.getString("product_code"),
                rs.getString("app_code"),
                rs.getString("version"),
                rs.getString("previous_version"),
                rs.getString("mode"),
                mapper.readValue(rs.getString("entries"), KEYS),
                "INSTALLED".equals(rs.getString("status"))), tenantId, instanceId);
        return rows.stream().findFirst();
    }

    @Override
    public List<InstanceSnapshot> list(UUID tenantId) {
        return jdbc.query("""
                SELECT instance_id, instance_revision, product_code, app_code, version, previous_version, mode,
                       entries::text AS entries, status
                  FROM business_meta.app_instance_state
                 WHERE tenant_id = ?
                 ORDER BY app_code, instance_id
                """, (rs, i) -> new InstanceSnapshot(
                rs.getObject("instance_id", UUID.class),
                rs.getLong("instance_revision"),
                rs.getString("product_code"),
                rs.getString("app_code"),
                rs.getString("version"),
                rs.getString("previous_version"),
                rs.getString("mode"),
                mapper.readValue(rs.getString("entries"), KEYS),
                "INSTALLED".equals(rs.getString("status"))), tenantId);
    }

    @Override
    public void save(UUID tenantId, InstanceSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO business_meta.app_instance_state
                    (tenant_id, instance_id, instance_revision, product_code, app_code, version, previous_version,
                     mode, entries, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (tenant_id, instance_id)
                DO UPDATE SET instance_revision = EXCLUDED.instance_revision,
                              product_code = EXCLUDED.product_code,
                              app_code = EXCLUDED.app_code,
                              version = EXCLUDED.version,
                              previous_version = EXCLUDED.previous_version,
                              mode = EXCLUDED.mode,
                              entries = EXCLUDED.entries,
                              status = EXCLUDED.status
                """,
                tenantId, snapshot.instanceId(), snapshot.revision(), snapshot.productCode(), snapshot.appCode(),
                snapshot.version(), snapshot.previousVersion(), snapshot.mode(),
                mapper.writeValueAsString(snapshot.entries()),
                snapshot.installed() ? "INSTALLED" : "UNINSTALLED");
    }
}
