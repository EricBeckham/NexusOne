package one.nexus.p5.connect.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p5.connect.ApiCatalogEntry;
import one.nexus.p5.connect.ApiCatalogRepository;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("flyway")
public class JdbcApiCatalogRepository implements ApiCatalogRepository {
    private static final String COLUMNS =
            "id, tenant_id, name, http_method, path, version, scope, status, description, created_at, updated_at";

    private final JdbcClient jdbc;

    public JdbcApiCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ApiCatalogEntry> listVisible(UUID tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM integration.api_catalog_entry WHERE " + VISIBLE_TENANT
                        + " ORDER BY name, http_method, path")
                .param("tenant", tenantId)
                .query(JdbcApiCatalogRepository::map)
                .list();
    }

    @Override
    public Optional<ApiCatalogEntry> findVisible(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM integration.api_catalog_entry WHERE id = :id AND " + VISIBLE_TENANT)
                .param("id", id)
                .param("tenant", tenantId)
                .query(JdbcApiCatalogRepository::map)
                .optional();
    }

    private static ApiCatalogEntry map(ResultSet row, int index) throws SQLException {
        return new ApiCatalogEntry(
                row.getObject("id", UUID.class),
                row.getObject("tenant_id", UUID.class),
                row.getString("name"),
                row.getString("http_method"),
                row.getString("path"),
                row.getString("version"),
                row.getString("scope"),
                row.getString("status"),
                row.getString("description"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
