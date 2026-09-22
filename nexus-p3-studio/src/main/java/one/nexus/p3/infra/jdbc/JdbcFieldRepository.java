package one.nexus.p3.infra.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.port.FieldRepository;

@Repository
@DependsOnDatabaseInitialization
public class JdbcFieldRepository implements FieldRepository {
    private final JdbcClient jdbc;

    public JdbcFieldRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LedgerField field) {
        jdbc.sql("""
                        INSERT INTO business_meta.ledger_field (
                          id, tenant_id, ledger_id, field_code, display_name, field_type,
                          required, indexed, sort_order,
                          created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (
                          :id, :tenant_id, :ledger_id, :field_code, :display_name, :field_type,
                          :required, :indexed, :sort_order,
                          :created_at, :created_by, :updated_at, :updated_by, :deleted_at, :version)
                        """)
                .param("id", field.id())
                .param("tenant_id", field.tenantId())
                .param("ledger_id", field.ledgerId())
                .param("field_code", field.fieldCode())
                .param("display_name", field.displayName())
                .param("field_type", field.fieldType().name())
                .param("required", field.required())
                .param("indexed", field.indexed())
                .param("sort_order", field.sortOrder())
                .param("created_at", JdbcValues.timestamp(field.createdAt()))
                .param("created_by", field.createdBy())
                .param("updated_at", JdbcValues.timestamp(field.updatedAt()))
                .param("updated_by", field.updatedBy())
                .param("deleted_at", JdbcValues.timestamp(field.deletedAt()))
                .param("version", field.version())
                .update();
    }

    @Override
    public List<LedgerField> listByLedger(UUID tenantId, UUID ledgerId) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger_field
                        WHERE tenant_id = :tenant_id AND ledger_id = :ledger_id AND deleted_at IS NULL
                        ORDER BY sort_order, created_at, id
                        """)
                .param("tenant_id", tenantId)
                .param("ledger_id", ledgerId)
                .query(this::map)
                .list();
    }

    @Override
    public Optional<LedgerField> findByCode(UUID tenantId, UUID ledgerId, String fieldCode) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.ledger_field
                        WHERE tenant_id = :tenant_id AND ledger_id = :ledger_id
                          AND field_code = :field_code AND deleted_at IS NULL
                        """)
                .param("tenant_id", tenantId)
                .param("ledger_id", ledgerId)
                .param("field_code", fieldCode)
                .query(this::map)
                .optional();
    }

    private LedgerField map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LedgerField(
                JdbcValues.uuid(rs.getObject("id")),
                JdbcValues.uuid(rs.getObject("tenant_id")),
                JdbcValues.uuid(rs.getObject("ledger_id")),
                rs.getString("field_code"),
                rs.getString("display_name"),
                FieldType.valueOf(rs.getString("field_type")),
                rs.getBoolean("required"),
                rs.getBoolean("indexed"),
                rs.getInt("sort_order"),
                JdbcValues.instant(rs.getTimestamp("created_at")),
                rs.getString("created_by"),
                JdbcValues.instant(rs.getTimestamp("updated_at")),
                rs.getString("updated_by"),
                JdbcValues.instant(rs.getTimestamp("deleted_at")),
                rs.getLong("version"));
    }
}
