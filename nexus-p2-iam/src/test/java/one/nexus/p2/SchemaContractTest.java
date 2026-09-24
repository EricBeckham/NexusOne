package one.nexus.p2;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractTest {
    @Test
    void tenantTablesRequireTenantId() throws Exception {
        String text;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V200__iam_identity.sql"),
                "migration not on classpath")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("CREATE SCHEMA IF NOT EXISTS iam"), "M0-04 tables belong in schema iam");
        for (String table : new String[]{
                "iam_tenant_membership", "iam_person", "iam_department",
                "iam_position", "iam_employment", "iam_issued_context"
        }) {
            int start = text.indexOf("CREATE TABLE iam." + table);
            int end = text.indexOf(";", start);
            assertTrue(start >= 0 && end > start, table + " must exist in schema iam");
            String body = text.substring(start, end);
            assertTrue(body.contains("tenant_id") && body.contains("NOT NULL"),
                    table + " must have tenant_id NOT NULL");
        }
        int start = text.indexOf("CREATE TABLE iam.iam_global_account");
        int end = text.indexOf(";", start);
        String global = text.substring(start, end);
        assertFalse(global.contains("tenant_id"),
                "global account is platform-scoped and must not carry tenant_id");
    }

    @Test
    void idpConfigurationStoresTenantMetadataWithoutCredentials() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V201__iam_idp_configuration.sql"),
                "migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("CREATE TABLE iam.iam_idp_configuration"));

        int start = sql.indexOf("CREATE TABLE iam.iam_idp_configuration");
        int end = sql.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "IdP configuration table must be present");
        String table = sql.substring(start, end).toLowerCase();
        for (String column : new String[]{
                "tenant_id", "provider_type", "display_name", "issuer", "status",
                "last_sync_at", "last_sync_ok", "sync_fail_count", "created_at", "updated_at"
        }) {
            assertTrue(table.matches("(?s).*\\b" + column + "\\b.*"), column + " metadata column must exist");
        }
        for (String forbidden : new String[]{"secret", "credential", "password", "private_key"}) {
            assertFalse(table.contains(forbidden), "IdP configuration must not store " + forbidden);
        }
        assertTrue(sql.contains("sync_fail_count >= 0"), "sync failure count must be non-negative");
    }

    @Test
    void idpConfigurationExtensionAddsOnlyPublicClientIdAndDeletionMarker() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V202__iam_idp_client_id.sql"),
                "migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        assertTrue(sql.contains("add column client_id varchar(256)"));
        assertTrue(sql.contains("add column deleted_at timestamptz"));
        assertFalse(sql.contains("client_secret"));
        assertFalse(sql.contains("password"));
    }
}
