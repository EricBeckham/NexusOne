package one.nexus.p1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SchemaContractTest {
    @Test
    void tenantParentMigrationIsOptionalAndIndexed() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V102__p1_tenant_parent.sql"),
                "migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.matches("(?is).*ADD\\s+COLUMN\\s+parent_tenant_id\\s+uuid\\s+NULL\\s+REFERENCES\\s+platform\\.tenant\\s*\\(\\s*id\\s*\\).*"),
                "parent_tenant_id must be nullable and reference platform.tenant");
        assertTrue(sql.matches("(?is).*CREATE\\s+INDEX\\s+p1_tenant_parent_tenant_idx\\s+ON\\s+platform\\.tenant\\s*\\(\\s*parent_tenant_id\\s*\\).*"),
                "parent_tenant_id must be indexed");
        assertFalse(sql.matches("(?is).*parent_tenant_id\\s+uuid\\s+NOT\\s+NULL.*"),
                "existing tenant rows must remain valid without a parent");
    }
}
