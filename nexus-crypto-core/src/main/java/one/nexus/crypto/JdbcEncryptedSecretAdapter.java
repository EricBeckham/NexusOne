package one.nexus.crypto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/** PostgreSQL-backed SecretPort that persists only CryptoPort ciphertext envelopes. */
public final class JdbcEncryptedSecretAdapter implements SecretPort {
    private static final String UPSERT = """
            INSERT INTO platform.secret_ciphertext (secret_name, version, ciphertext, key_id, updated_at)
            VALUES (?, 1, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (secret_name) DO UPDATE SET
                version = platform.secret_ciphertext.version + 1,
                ciphertext = EXCLUDED.ciphertext,
                key_id = EXCLUDED.key_id,
                updated_at = CURRENT_TIMESTAMP
            RETURNING version, updated_at
            """;
    private static final String SELECT = """
            SELECT version, ciphertext, key_id, updated_at
              FROM platform.secret_ciphertext
             WHERE secret_name = ?
            """;

    private final DataSource dataSource;
    private final CryptoPort crypto;

    public JdbcEncryptedSecretAdapter(DataSource dataSource, CryptoPort crypto) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    @Override
    public SecretRef put(String name, byte[] plaintext) {
        requireName(name);
        if (plaintext == null) {
            throw new NexusException(ErrorCode.SECRET_PLAINTEXT_FORBIDDEN, 400, "secret plaintext is required");
        }
        byte[] ciphertext = crypto.encrypt(plaintext).encode();
        SealedBytes sealed = SealedBytes.decode(ciphertext);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, name);
            statement.setBytes(2, ciphertext);
            statement.setString(3, sealed.key().keyId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw storageUnavailable();
                return new SecretRef(name, row.getInt("version"), sealed.key(), instant(row, "updated_at"));
            }
        } catch (SQLException ex) {
            throw storageUnavailable();
        }
    }

    @Override
    public byte[] get(String name) {
        requireName(name);
        Stored stored = findStored(name).orElseThrow(() ->
                new NexusException(ErrorCode.SECRET_NOT_FOUND, 404, "secret not found"));
        return crypto.decrypt(stored.sealed());
    }

    @Override
    public Optional<SecretRef> find(String name) {
        requireName(name);
        return findStored(name).map(Stored::reference);
    }

    private Optional<Stored> findStored(String name) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, name);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                byte[] ciphertext = row.getBytes("ciphertext");
                SealedBytes sealed = SealedBytes.decode(ciphertext);
                if (!sealed.key().keyId().equals(row.getString("key_id"))) {
                    throw new NexusException(ErrorCode.CRYPTO_INTEGRITY, 400, "secret key metadata mismatch");
                }
                int version = row.getInt("version");
                Instant updatedAt = instant(row, "updated_at");
                return Optional.of(new Stored(
                        new SecretRef(name, version, sealed.key(), updatedAt), sealed));
            }
        } catch (SQLException ex) {
            throw storageUnavailable();
        }
    }

    @Override
    public void delete(String name) {
        requireName(name);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM platform.secret_ciphertext WHERE secret_name = ?")) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw storageUnavailable();
        }
    }

    @Override
    public CryptoSuite suite() {
        return crypto.suite();
    }

    @Override
    public String backend() {
        return "jdbc";
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp.toInstant();
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 512) {
            throw new IllegalArgumentException("secret name is required and must not exceed 512 characters");
        }
    }

    private static NexusException storageUnavailable() {
        return new NexusException(ErrorCode.API_INTERNAL, 503, "secret storage unavailable");
    }

    private record Stored(SecretRef reference, SealedBytes sealed) {}
}
