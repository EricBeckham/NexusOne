package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class CredentialMetadataSchemaContractTest {
    @Test
    void credentialMetadataStoresSecretPortReferenceButNeverSecretMaterial() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V704__p5_credential_metadata.sql"),
                "M16 migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        int start = sql.indexOf("create table integration.credential_metadata");
        int end = sql.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "credential metadata table must exist");
        String table = sql.substring(start, end);
        for (String column : new String[]{"tenant_id", "name", "provider", "status", "last_rotated_at", "expires_at", "secret_name"}) {
            assertTrue(table.matches("(?s).*\\b" + column + "\\b.*"), column + " metadata column must exist");
        }
        for (String forbidden : new String[]{"credential_value", "secret_value", "secret_plaintext", "password", "api_key", "access_token", "refresh_token"}) {
            assertFalse(table.contains(forbidden), "credential metadata must not persist " + forbidden);
        }
        assertTrue(table.contains("secret_name varchar"), "only a SecretPort reference may be stored");
        assertTrue(table.contains("unique (tenant_id, name)"), "credential names must be unique per tenant");
        assertTrue(sql.contains("on integration.credential_metadata (tenant_id, updated_at desc)"),
                "tenant-scoped listing must have an index");
    }
}
