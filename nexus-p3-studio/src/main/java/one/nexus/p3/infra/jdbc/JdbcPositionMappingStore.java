package one.nexus.p3.infra.jdbc;

import java.util.UUID;
import one.nexus.p3.instance.PositionMappingStore;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PLX1 C2. Position codes only; no position names. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcPositionMappingStore implements PositionMappingStore {
    private final JdbcTemplate jdbc;

    public JdbcPositionMappingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean mapped(UUID tenantId, UUID instanceId, String positionCode) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM business_meta.instance_position_mapping
                 WHERE tenant_id = ? AND instance_id = ? AND position_code = ?
                """, Integer.class, tenantId, instanceId, positionCode);
        return count != null && count > 0;
    }

    @Override
    public void map(UUID tenantId, UUID instanceId, String positionCode, UUID positionId) {
        jdbc.update("""
                INSERT INTO business_meta.instance_position_mapping
                    (tenant_id, instance_id, position_code, position_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, instance_id, position_code)
                DO UPDATE SET position_id = EXCLUDED.position_id
                """, tenantId, instanceId, positionCode, positionId);
    }

    @Override
    public void unmap(UUID tenantId, UUID instanceId, String positionCode) {
        jdbc.update("""
                DELETE FROM business_meta.instance_position_mapping
                 WHERE tenant_id = ? AND instance_id = ? AND position_code = ?
                """, tenantId, instanceId, positionCode);
    }
}
