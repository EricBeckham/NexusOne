package one.nexus.p3.flyway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ProductApplicationSchemaContractTest {
    @Test
    void applicationTableIsTenantScopedAndDoesNotExposeWithdrawn() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V552__product_application.sql"),
                "K14 migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("create table business_meta.product_application"));
        for (String column : new String[]{
                "tenant_id", "installation_id", "product_definition_id", "form_template_version_id",
                "process_instance_id", "values_json", "status", "initiator_person_id"}) {
            assertTrue(sql.matches("(?s).*\\b" + column + "\\b.*"), column + " must be present");
        }
        int statusCheck = sql.indexOf("product_application_status check");
        assertTrue(statusCheck >= 0, "status check must exist");
        String statusClause = sql.substring(statusCheck, sql.indexOf(')', statusCheck));
        assertTrue(statusClause.contains("'running'") && statusClause.contains("'approved'") && statusClause.contains("'rejected'"));
        assertFalse(statusClause.contains("withdrawn"), "first version must not expose WITHDRAWN");
        assertTrue(sql.contains("on business_meta.product_application (tenant_id, created_at desc)"),
                "applications must have a tenant-scoped index");
        assertTrue(sql.contains("'product_application', 'tenant', true"),
                "table_registry must mark the table tenant-required");
        assertTrue(sql.contains("installation_id is not null or product_definition_id is not null"));
        assertFalse(sql.contains("create table flowable."), "Flowable owns its engine tables");
        assertFalse(sql.contains("create table market."), "market product definition belongs to K8");
    }
}
