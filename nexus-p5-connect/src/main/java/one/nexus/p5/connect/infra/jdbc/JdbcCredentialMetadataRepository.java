package one.nexus.p5.connect.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
    public Optional<CredentialMetadata> find(UUID tenantId, UUID id) {
        return jdbc.sql(FIND_SQL)
                .param("id", id)
                .param("tenant", tenantId)
                .query(JdbcCredentialMetadataRepository::map)
                .optional();
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

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
