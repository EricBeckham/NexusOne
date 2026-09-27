package one.nexus.p3.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.port.ProductApplicationStore;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for {@link ProductApplicationStore}. Reads the market pin with an
 * explicit {@code tenant_id} predicate, the platform template snapshot by id, and
 * {@code business_meta.product_application} / {@code process_todo} scoped by tenant.
 */
@Repository
@DependsOnDatabaseInitialization
public class JdbcProductApplicationStore implements ProductApplicationStore {
    private final JdbcTemplate jdbc;

    public JdbcProductApplicationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<InstalledPin> findInstalledPin(UUID tenantId, UUID installationId) {
        return Optional.ofNullable(jdbc.query("""
                SELECT p.form_template_version_id, p.product_definition_id,
                       p.process_definition_key, p.process_definition_version
                  FROM market.installation_product_pin p
                  JOIN market.installation i ON i.id = p.installation_id
                 WHERE p.installation_id = ? AND i.tenant_id = ? AND i.status = 'INSTALLED'
                """, rs -> rs.next() ? new InstalledPin(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), (Integer) rs.getObject(4)) : null,
                installationId, tenantId));
    }

    @Override
    public Optional<ApplicationSnapshot> findForTenant(UUID tenantId, UUID applicationId) {
        return Optional.ofNullable(jdbc.query("""
                SELECT id, installation_id, product_definition_id, form_template_version_id, values_json::text,
                       status, initiator_person_id, created_at
                  FROM business_meta.product_application
                 WHERE id = ? AND tenant_id = ?
                """, rs -> rs.next() ? new ApplicationSnapshot(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant()) : null,
                applicationId, tenantId));
    }

    @Override
    public Optional<String> formTemplateSnapshot(UUID formTemplateVersionId) {
        return Optional.ofNullable(jdbc.query(
                "SELECT fields_snapshot::text FROM platform.form_template_version WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, formTemplateVersionId));
    }

    @Override
    public List<ApplicationRow> listByInitiator(UUID tenantId, UUID initiatorPersonId) {
        return jdbc.query("""
                SELECT a.id, a.installation_id, a.created_at, a.process_instance_id,
                       COALESCE(i.status, a.status) AS status
                  FROM business_meta.product_application a
                  LEFT JOIN business_meta.process_instance i
                    ON i.id = a.process_instance_id AND i.tenant_id = a.tenant_id
                 WHERE a.tenant_id = ? AND a.initiator_person_id = ?
                 ORDER BY a.created_at DESC
                """, (rs, row) -> new ApplicationRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("installation_id", UUID.class),
                        rs.getString("status"),
                        rs.getObject("process_instance_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId, initiatorPersonId);
    }

    @Override
    public void insertRunning(NewApplication application) {
        jdbc.update("""
                INSERT INTO business_meta.product_application
                    (id, tenant_id, installation_id, product_definition_id, form_template_version_id,
                     process_instance_id, values_json, status, initiator_person_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, CAST(? AS jsonb), 'RUNNING', ?, ?, ?)
                """, application.id(), application.tenantId(), application.installationId(),
                application.productDefinitionId(), application.formTemplateVersionId(), application.valuesJson(),
                application.initiatorPersonId(), sqlTimestamp(application.createdAt()),
                sqlTimestamp(application.createdAt()));
    }

    @Override
    public void attachProcessInstance(UUID tenantId, UUID applicationId, UUID processInstanceId, Instant updatedAt) {
        jdbc.update("UPDATE business_meta.product_application SET process_instance_id=?, updated_at=? WHERE id=? AND tenant_id=?",
                processInstanceId, sqlTimestamp(updatedAt), applicationId, tenantId);
    }

    @Override
    public Optional<String> latestActedTaskId(UUID tenantId, UUID processInstanceId) {
        return Optional.ofNullable(jdbc.query("""
                SELECT flowable_task_id FROM business_meta.process_todo
                 WHERE tenant_id = ? AND instance_id = ? AND outcome IS NOT NULL
                 ORDER BY acted_at DESC NULLS LAST
                 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, tenantId, processInstanceId));
    }
}
