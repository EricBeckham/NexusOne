package one.nexus.p3.flyway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDraftSchemaContractTest {
    @Test
    void gatewayTypesNeedNoEnumColumnAndLayoutFitsExistingJsonContainers() throws Exception {
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/V551__process_definition_draft.sql"))) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertTrue(sql.contains("bpmn_xml text not null"));
            assertFalse(sql.contains("gateway_type"));
        }
        String templates = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../nexus-bootstrap/src/main/resources/db/migration/V005__platform_form_template.sql")).toLowerCase();
        assertTrue(templates.contains("options jsonb"));
        assertTrue(templates.contains("fields_snapshot jsonb not null"));
    }
    @Test
    void draftTableIsTenantScopedAndDoesNotDefinePublishedStateOrEngineTables() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V551__process_definition_draft.sql"),
                "M5 migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("create table business_meta.process_definition_draft"));
        for (String column : new String[]{"tenant_id", "process_key", "name", "bpmn_xml", "version", "updated_by", "updated_at"}) {
            assertTrue(sql.matches("(?s).*\\b" + column + "\\b.*"), column + " must be present");
        }
        assertTrue(sql.contains("unique (tenant_id, process_key)"), "draft key must be unique within a tenant");
        assertTrue(sql.contains("on business_meta.process_definition_draft (tenant_id, updated_at desc)"),
                "draft listing must have a tenant-scoped index");
        assertFalse(sql.contains("create table flowable."), "Flowable owns its engine tables");
        int tableStart = sql.indexOf("create table business_meta.process_definition_draft");
        int tableEnd = sql.indexOf(";", tableStart);
        String table = sql.substring(tableStart, tableEnd);
        assertFalse(table.contains("published"), "publication state belongs to M10");
    }
}
