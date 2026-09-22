package one.nexus.p3.infra.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.ApplicationStatus;
import one.nexus.p3.port.ApplicationRepository;

@Repository
@DependsOnDatabaseInitialization
public class JdbcApplicationRepository implements ApplicationRepository {
    private final JdbcClient jdbc;

    public JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Application application) {
        jdbc.sql("""
                        INSERT INTO business_meta.application (
                          id, tenant_id, tenant_code, app_code, display_name, kind, status,
                          created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (
                          :id, :tenant_id, :tenant_code, :app_code, :display_name, :kind, :status,
                          :created_at, :created_by, :updated_at, :updated_by, :deleted_at, :version)
                        """)
                .param("id", application.id())
                .param("tenant_id", application.tenantId())
                .param("tenant_code", application.tenantCode())
                .param("app_code", application.appCode())
                .param("display_name", application.displayName())
                .param("kind", application.kind().name())
                .param("status", application.status().name())
                .param("created_at", JdbcValues.timestamp(application.createdAt()))
                .param("created_by", application.createdBy())
                .param("updated_at", JdbcValues.timestamp(application.updatedAt()))
                .param("updated_by", application.updatedBy())
                .param("deleted_at", JdbcValues.timestamp(application.deletedAt()))
                .param("version", application.version())
                .update();
    }

    @Override
    public Optional<Application> findById(UUID tenantId, UUID id) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.application
                        WHERE tenant_id = :tenant_id AND id = :id AND deleted_at IS NULL
                        """)
                .param("tenant_id", tenantId)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Application> findByCode(UUID tenantId, String appCode) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.application
                        WHERE tenant_id = :tenant_id AND app_code = :app_code AND deleted_at IS NULL
                        """)
                .param("tenant_id", tenantId)
                .param("app_code", appCode)
                .query(this::map)
                .optional();
    }

    @Override
    public List<Application> list(UUID tenantId) {
        return jdbc.sql("""
                        SELECT * FROM business_meta.application
                        WHERE tenant_id = :tenant_id AND deleted_at IS NULL
                        ORDER BY created_at, id
                        """)
                .param("tenant_id", tenantId)
                .query(this::map)
                .list();
    }

    private Application map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Application(
                JdbcValues.uuid(rs.getObject("id")),
                JdbcValues.uuid(rs.getObject("tenant_id")),
                rs.getString("tenant_code"),
                rs.getString("app_code"),
                rs.getString("display_name"),
                ApplicationKind.valueOf(rs.getString("kind")),
                ApplicationStatus.valueOf(rs.getString("status")),
                JdbcValues.instant(rs.getTimestamp("created_at")),
                rs.getString("created_by"),
                JdbcValues.instant(rs.getTimestamp("updated_at")),
                rs.getString("updated_by"),
                JdbcValues.instant(rs.getTimestamp("deleted_at")),
                rs.getLong("version"));
    }
}
