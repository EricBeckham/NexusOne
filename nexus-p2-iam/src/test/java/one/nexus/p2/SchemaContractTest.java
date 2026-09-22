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
}
