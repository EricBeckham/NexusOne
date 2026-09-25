package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService.ValidationError;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService.ValidationResult;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService.TemplateView;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ProductDefinitionWrite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * H8: the platform template → product-definition pages talk to records serialized by the product's
 * configured mapper (SNAKE_CASE). Pins the wire names the web client must read and send.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class PlatformBindingWireNamingTest {
    private static EmbeddedPostgres postgres;

    @Autowired ObjectMapper mapper;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void templateAndProductDefinitionResponsesUseSnakeCase() {
        UUID version = UUID.randomUUID();
        JsonNode template = mapper.valueToTree(new TemplateView(UUID.randomUUID(), "leave", "请假", null, "PUBLISHED",
                version, Instant.now(), Instant.now(), 1, List.of()));
        assertEquals(version.toString(), template.path("running_version_id").asText());
        assertTrue(template.path("runningVersionId").isMissingNode());

        JsonNode definition = mapper.valueToTree(new ProductDefinition(UUID.randomUUID(), "hr", "HR", null, version,
                "leave_flow", 3, Map.of(), "DRAFT", Instant.now(), Instant.now(), 1));
        assertEquals(version.toString(), definition.path("form_template_version_id").asText());
        assertEquals("leave_flow", definition.path("process_definition_key").asText());
        assertEquals(3, definition.path("process_definition_version").asInt());
    }

    @Test
    void productDefinitionWriteOnlyBindsSnakeCaseKeys() throws Exception {
        String snake = """
                {"code":"hr","name":"HR","form_template_version_id":"%s","process_definition_key":"leave_flow",
                 "process_definition_version":3,"default_permission_pack":{"summary":"s"}}""".formatted(UUID.randomUUID());
        ProductDefinitionWrite bound = mapper.readValue(snake, ProductDefinitionWrite.class);
        assertEquals("leave_flow", bound.processDefinitionKey());
        assertEquals(3, bound.processDefinitionVersion());

        String camel = """
                {"code":"hr","name":"HR","formTemplateVersionId":"%s","processDefinitionKey":"leave_flow",
                 "processDefinitionVersion":3,"defaultPermissionPack":{"summary":"s"}}""".formatted(UUID.randomUUID());
        ProductDefinitionWrite ignored = mapper.readValue(camel, ProductDefinitionWrite.class);
        assertNull(ignored.formTemplateVersionId(), "camelCase keys are not bound, so the server sees no template version");
        assertNull(ignored.processDefinitionKey());
    }

    @Test
    void formTemplateValidationErrorsExposeFieldKeyInSnakeCase() {
        UUID fieldId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        JsonNode body = mapper.valueToTree(new ValidationResult(templateId, false, "DRAFT", List.of(
                new ValidationError(fieldId, "Reason", "field.key.invalid", "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线"),
                new ValidationError(null, null, "fields.required", "至少需要一个字段"))));

        assertEquals(templateId.toString(), body.path("template_id").asText());
        assertTrue(body.path("templateId").isMissingNode());
        assertEquals(false, body.path("valid").asBoolean());
        assertEquals("DRAFT", body.path("status").asText());

        JsonNode fieldError = body.path("errors").get(0);
        assertEquals(fieldId.toString(), fieldError.path("field_id").asText());
        assertEquals("Reason", fieldError.path("field_key").asText());
        assertEquals("field.key.invalid", fieldError.path("code").asText());
        assertTrue(fieldError.path("message").asText().contains("小写字母"));
        assertTrue(fieldError.path("fieldKey").isMissingNode(), "web must read field_key, not camelCase");

        JsonNode formError = body.path("errors").get(1);
        assertTrue(formError.path("field_key").isNull() || formError.path("field_key").isMissingNode());
        assertEquals("fields.required", formError.path("code").asText());
        assertEquals("至少需要一个字段", formError.path("message").asText());
    }
}
