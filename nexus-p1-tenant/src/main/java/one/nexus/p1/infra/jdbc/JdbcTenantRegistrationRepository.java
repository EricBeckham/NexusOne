package one.nexus.p1.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import one.nexus.p1.domain.RegistrationStatus;
import one.nexus.p1.domain.TenantRegistrationApplication;
import one.nexus.p1.port.TenantRegistrationRepository;

@Repository
@DependsOn("flyway")
public class JdbcTenantRegistrationRepository implements TenantRegistrationRepository {

    private final JdbcClient jdbc;

    public JdbcTenantRegistrationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TenantRegistrationApplication application) {
        jdbc.sql("""
                INSERT INTO platform.tenant_registration_application (
                  id, org_name, admin_display_name, admin_email, contact_note, status,
                  review_note, reviewed_at, reviewed_by, resulting_tenant_id, created_at)
                VALUES (
                  :id, :org_name, :admin_display_name, :admin_email, :contact_note, :status,
                  :review_note, :reviewed_at, :reviewed_by, :resulting_tenant_id, :created_at)
                """)
                .param("id", application.id())
                .param("org_name", application.orgName())
                .param("admin_display_name", application.adminDisplayName())
                .param("admin_email", application.adminEmail())
                .param("contact_note", application.contactNote())
                .param("status", application.status().name())
                .param("review_note", application.reviewNote())
                .param("reviewed_at", JdbcValues.timestamp(application.reviewedAt()))
                .param("reviewed_by", application.reviewedBy())
                .param("resulting_tenant_id", application.resultingTenantId())
                .param("created_at", JdbcValues.timestamp(application.createdAt()))
                .update();
    }

    @Override
    public void update(TenantRegistrationApplication application) {
        jdbc.sql("""
                UPDATE platform.tenant_registration_application SET
                  status = :status,
                  review_note = :review_note,
                  reviewed_at = :reviewed_at,
                  reviewed_by = :reviewed_by,
                  resulting_tenant_id = :resulting_tenant_id
                WHERE id = :id
                """)
                .param("id", application.id())
                .param("status", application.status().name())
                .param("review_note", application.reviewNote())
                .param("reviewed_at", JdbcValues.timestamp(application.reviewedAt()))
                .param("reviewed_by", application.reviewedBy())
                .param("resulting_tenant_id", application.resultingTenantId())
                .update();
    }

    @Override
    public Optional<TenantRegistrationApplication> findById(UUID id) {
        return jdbc.sql("SELECT * FROM platform.tenant_registration_application WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<TenantRegistrationApplication> list(RegistrationStatus status) {
        if (status == null) {
            return jdbc.sql("""
                    SELECT * FROM platform.tenant_registration_application
                    ORDER BY created_at DESC
                    """)
                    .query(this::map)
                    .list();
        }
        return jdbc.sql("""
                SELECT * FROM platform.tenant_registration_application
                WHERE status = :status
                ORDER BY created_at DESC
                """)
                .param("status", status.name())
                .query(this::map)
                .list();
    }

    @Override
    public boolean existsPendingEmail(String adminEmail) {
        Integer count = jdbc.sql("""
                SELECT COUNT(*) FROM platform.tenant_registration_application
                WHERE status = 'PENDING' AND admin_email = :email
                """)
                .param("email", adminEmail.trim().toLowerCase())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private TenantRegistrationApplication map(ResultSet rs, int rowNum) throws SQLException {
        return new TenantRegistrationApplication(
                (UUID) rs.getObject("id"),
                rs.getString("org_name"),
                rs.getString("admin_display_name"),
                rs.getString("admin_email"),
                rs.getString("contact_note"),
                RegistrationStatus.valueOf(rs.getString("status")),
                rs.getString("review_note"),
                JdbcValues.instant(rs, "reviewed_at"),
                (UUID) rs.getObject("reviewed_by"),
                (UUID) rs.getObject("resulting_tenant_id"),
                JdbcValues.instant(rs, "created_at"));
    }
}