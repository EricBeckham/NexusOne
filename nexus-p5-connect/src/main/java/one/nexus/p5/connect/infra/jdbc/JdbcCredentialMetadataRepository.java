package one.nexus.p5.connect.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p5.connect.CredentialMetadata;
import one.nexus.p5.connect.CredentialMetadataRepository;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("flyway")
public class JdbcCredentialMetadataRepository implements CredentialMetadataRepository {
    public static final String LIST_SQL = """
            SELECT id, tenant_id, name, provider, status, last_rotated_at, expires_at, secret_name, created_at, updated_at
            FROM integration.credential_metadata
            WHERE tenant_id = :tenant
            ORDER BY updated_at DESC, name
            """;

    public static final String FIND_SQL = """
            SELECT id, tenant_id, name, provider, status, last_rotated_at, expires_at, secret_name, created_at, updated_at
            FROM integration.credential_metadata
            WHERE id = :id AND tenant_id = :tenant
            """;

    private final JdbcClient jdbc;

    public JdbcCredentialMetadataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CredentialMetadata> list(UUID tenantId) {
        return jdbc.sql(LIST_SQL).param("tenant", tenantId).query(JdbcCredentialMetadataRepository::map).list();
    }

    @Override
    public Optional<CredentialMetadata> find(UUID tenantId, UUID id) {
        return jdbc.sql(FIND_SQL)
                .param("id", id)
                .param("tenant", tenantId)
                .query(JdbcCredentialMetadataRepository::map)
                .optional();
    }

    @Override
    public Optional<CredentialMetadata> findByName(UUID tenantId, String name) {
        return jdbc.sql("""
                SELECT id, tenant_id, name, provider, status, last_rotated_at, expires_at, secret_name, created_at, updated_at
                FROM integration.credential_metadata
                WHERE tenant_id = :tenant AND name = :name
                """)
                .param("tenant", tenantId)
                .param("name", name)
                .query(JdbcCredentialMetadataRepository::map)
                .optional();
    }

    @Override
    public void insert(CredentialMetadata row) {
        jdbc.sql("""
                INSERT INTO integration.credential_metadata
                    (id, tenant_id, name, provider, status, last_rotated_at, expires_at, secret_name, created_at, updated_at)
                VALUES
                    (:id, :tenant, :name, :provider, :status, :rotated, :expires, :reference, :created, :updated)
                """)
                .param("id", row.id())
                .param("tenant", row.tenantId())
                .param("name", row.name())
                .param("provider", row.provider())
                .param("status", row.status())
                .param("rotated", timestamp(row.lastRotatedAt()))
                .param("expires", timestamp(row.expiresAt()))
                .param("reference", row.secretName())
                .param("created", Timestamp.from(row.createdAt()))
                .param("updated", Timestamp.from(row.updatedAt()))
                .update();
    }

    @Override
    public boolean update(UUID tenantId, UUID id, String name, String provider, Instant expiresAt, Instant updatedAt) {
        return jdbc.sql("""
                UPDATE integration.credential_metadata
                SET name = :name, provider = :provider, expires_at = :expires, updated_at = :updated
                WHERE id = :id AND tenant_id = :tenant
                """)
                .param("name", name)
                .param("provider", provider)
                .param("expires", timestamp(expiresAt))
                .param("updated", Timestamp.from(updatedAt))
                .param("id", id)
                .param("tenant", tenantId)
                .update() > 0;
    }

    @Override
    public boolean markRotated(UUID tenantId, UUID id, Instant rotatedAt) {
        Timestamp at = Timestamp.from(rotatedAt);
        return jdbc.sql("""
                UPDATE integration.credential_metadata
                SET last_rotated_at = :at, updated_at = :at
                WHERE id = :id AND tenant_id = :tenant AND status = 'ACTIVE'
                """)
                .param("at", at)
                .param("id", id)
                .param("tenant", tenantId)
                .update() > 0;
    }

    @Override
    public boolean markRevoked(UUID tenantId, UUID id, Instant revokedAt) {
        return jdbc.sql("""
                UPDATE integration.credential_metadata
                SET status = 'REVOKED', updated_at = :at
                WHERE id = :id AND tenant_id = :tenant AND status = 'ACTIVE'
                """)
                .param("at", Timestamp.from(revokedAt))
                .param("id", id)
                .param("tenant", tenantId)
                .update() > 0;
    }

    private static CredentialMetadata map(ResultSet row, int index) throws SQLException {
        return new CredentialMetadata(
                row.getObject("id", UUID.class),
                row.getObject("tenant_id", UUID.class),
                row.getString("name"),
                row.getString("provider"),
                row.getString("status"),
                instant(row, "last_rotated_at"),
                instant(row, "expires_at"),
                row.getString("secret_name"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
