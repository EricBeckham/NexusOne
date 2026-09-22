package one.nexus.permission.persist;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SchemaContractTest {
    @Test
    void tenantTablesRequireTenantIdAndStayInIamWindow() throws Exception {
        String text;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V300__permission_engine.sql"),
                "V300 missing")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("CREATE SCHEMA IF NOT EXISTS iam"));
        assertTrue(text.contains("V300") || text.contains("perm_"));
        for (String table : new String[]{
                "perm_policy_version", "perm_role", "perm_role_parent", "perm_assignment",
                "perm_grant", "perm_field_policy", "perm_record_share"
        }) {
            int start = text.indexOf("CREATE TABLE iam." + table);
            int end = text.indexOf(";", start);
            assertTrue(start >= 0 && end > start, table + " must exist in schema iam");
            String body = text.substring(start, end);
            assertTrue(body.contains("tenant_id") && body.contains("NOT NULL"),
                    table + " must have tenant_id NOT NULL");
        }
    }
}
