package one.nexus.p3.infra.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Component;
import one.nexus.common.id.UuidV7;
import one.nexus.common.module.NexusModule;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.P3Exception;

/** Inserts/updates {@code platform.table_registry} in the same transaction as SchemaService DDL. */
@Component
public class JdbcTableRegistryWriter {
    public void upsert(Connection connection, LedgerDefinition ledger) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement existing = connection.prepareStatement("""
                SELECT id, version FROM platform.table_registry
                WHERE table_schema = ? AND table_name = ? AND deleted_at IS NULL
                """)) {
            existing.setString(1, "business_data");
            existing.setString(2, ledger.physicalTable());
            try (ResultSet rs = existing.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE platform.table_registry
                            SET schema_version = ?, updated_at = ?, version = version + 1
                            WHERE id = ?
                            """)) {
                        update.setInt(1, ledger.schemaVersion());
                        update.setTimestamp(2, Timestamp.from(now));
                        update.setObject(3, rs.getObject("id"));
                        if (update.executeUpdate() != 1) {
                            throw P3Exception.tableConflict(ledger.physicalTable());
                        }
                    }
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO platform.table_registry (
                  id, table_schema, table_name, scope, tenant_required, schema_version,
                  owner_module, created_at, updated_at, deleted_at, version)
                VALUES (?, ?, ?, ?, true, ?, ?, ?, ?, NULL, 1)
                """)) {
            insert.setObject(1, UuidV7.generate());
            insert.setString(2, "business_data");
            insert.setString(3, ledger.physicalTable());
            insert.setString(4, ledger.scope().registryScope());
            insert.setInt(5, ledger.schemaVersion());
            insert.setString(6, NexusModule.P3_STUDIO);
            insert.setTimestamp(7, Timestamp.from(now));
            insert.setTimestamp(8, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    public boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'business_data' AND table_name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}
