package one.nexus.bootstrap.web;

import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.session.TenantLabelPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * DEMO-U6: tenant code / name for session membership views, read from P1 {@code platform.tenant}. Only labels —
 * membership, status and the tenant id used for switching still come from IAM.
 */
@Component
public class JdbcTenantLabels implements TenantLabelPort {
    private final JdbcClient jdbc;

    public JdbcTenantLabels(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantLabel> label(UUID tenantId) {
        if (tenantId == null) return Optional.empty();
        return jdbc.sql("SELECT code, name FROM platform.tenant WHERE id = :id AND deleted_at IS NULL")
                .param("id", tenantId)
                .query((rs, n) -> new TenantLabel(rs.getString("code"), rs.getString("name")))
                .optional();
    }
}
