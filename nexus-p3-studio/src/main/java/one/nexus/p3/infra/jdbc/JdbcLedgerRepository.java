package one.nexus.p3.infra.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.port.LedgerRepository;

@Repository
@DependsOn("flyway")
public class JdbcLedgerRepository implements LedgerRepository {
    private final JdbcClient jdbc;

    public JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LedgerDefinition ledger) {
        jdbc.sql("""
                        INSERT INTO business_meta.ledger (
                          id, tenant_id, tenant_code, application_id, ledger_code, display_name,
                          scope, physical_table, schema_version, status,
                          created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (
                          :id, :tenant_id, :tenant_code, :application_id, :ledger_code, :display_name,
                          :scope, :physical_table, :schema_version, :status,
                          :created_at, :created_by, :updated_at, :updated_by, :deleted_at, :version)
                        """)
                .param("id", ledger.id())
                .param("tenant_id", ledger.tenantId())
                .param("tenant_code", ledger.tenantCode())
                .param("application_id", ledger.applicationId())
                .param("ledger_code", ledger.ledgerCode())
                .param("display_name", ledger.displayName())
                .param("scope", ledger.scope().name())
                .param("physical_table", ledger.physicalTable())
                .param("schema_version", ledger.schemaVersion())
                .param("status", ledger.status().name())
                .param("created_at", JdbcValues.timestamp(ledger.createdAt()))
                .param("created_by", ledger.createdBy())
                .param("updated_at", JdbcValues.timestamp(ledger.updatedAt()))
                .param("updated_by", ledger.updatedBy())
                .param("deleted_at", JdbcValues.timestamp(ledger.deletedAt()))
                .param("version", ledger.version())
                .update();
    }

    @Override
    public void update(LedgerDefinition ledger) {
        int updated = jdbc.sql("""
                        UPDATE business_meta.ledger
                        SET schema_version = :schema_version,
                            status = :status,
                            updated_at = :updated_at,
                            updated_by = :updated_by,
                            version = :version
                        WHERE tenant_id = :tenant_id AND id = :id AND deleted_at IS NULL
                        """)
                .param("schema_version", ledger.schemaVersion())
                .param("status", ledger.status().name())
                .param("updated_at", JdbcValues.timestamp(ledger.updatedAt()))
                .param("updated_by", ledger.updatedBy())
                .param("version", ledger.version())
                .param("tenant_id", ledger.tenantId())
                .param("id", ledger.id())
                .update();
        if (updated != 1) {
            throw one.nexus.p3.domain.P3Exception.ledgerNotFound(ledger.id());
        }
    }

    @Override
    public Optional<LedgerDefinition> findById(UUID tenantId, UUID id) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger
                        WHERE tenant_id = :tenant_id AND id = :id AND deleted_at IS NULL
                        """)
                .param("tenant_id", tenantId)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<LedgerDefinition> findByCode(UUID tenantId, String ledgerCode) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger
                        WHERE tenant_id = :tenant_id AND ledger_code = :ledger_code AND deleted_at IS NULL
                        """)
                .param("tenant_id", tenantId)
                .param("ledger_code", ledgerCode)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<LedgerDefinition> findByPhysicalTable(String physicalTable) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger
                        WHERE physical_table = :physical_table AND deleted_at IS NULL
                        """)
                .param("physical_table", physicalTable)
                .query(this::map)
                .optional();
    }

    @Override
    public List<LedgerDefinition> list(UUID tenantId) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger
                        WHERE tenant_id = :tenant_id AND deleted_at IS NULL
                        ORDER BY created_at, id
                        """)
                .param("tenant_id", tenantId)
                .query(this::map)
                .list();
    }

    @Override
    public List<LedgerDefinition> listReady() {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger
                        WHERE status = 'READY' AND deleted_at IS NULL
                        ORDER BY created_at, id
                        """)
                .query(this::map)
                .list();
    }

    private LedgerDefinition map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LedgerDefinition(
                JdbcValues.uuid(rs.getObject("id")),
                JdbcValues.uuid(rs.getObject("tenant_id")),
                rs.getString("tenant_code"),
                JdbcValues.uuid(rs.getObject("application_id")),
                rs.getString("ledger_code"),
                rs.getString("display_name"),
                LedgerScope.valueOf(rs.getString("scope")),
                rs.getString("physical_table"),
                rs.getInt("schema_version"),
                LedgerStatus.valueOf(rs.getString("status")),
                JdbcValues.instant(rs.getTimestamp("created_at")),
                rs.getString("created_by"),
                JdbcValues.instant(rs.getTimestamp("updated_at")),
                rs.getString("updated_by"),
                JdbcValues.instant(rs.getTimestamp("deleted_at")),
                rs.getLong("version"));
    }
}
