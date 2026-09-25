package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * I7: the product-definition page's "published template versions" come from
 * GET /api/v1/p1/platform/form-templates. Pins, on the real app with demo seeds, that the seeded
 * template and a template created → validated → published through the API both come back as
 * PUBLISHED with a snake_case running_version_id, the field the page reads.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class PublishedTemplateVersionsTest {
    private static final String PLATFORM_ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

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
        String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.demo.enabled", () -> "true");
        registry.add("nexus.demo.environment", () -> "local");
        registry.add("nexus.p1.demo-context.enabled", () -> "true");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.iam.persistence", () -> "jdbc");
        registry.add("nexus.iam.jdbc.migrate", () -> "false");
        registry.add("nexus.iam.jdbc.url", () -> url);
        registry.add("nexus.iam.jdbc.username", () -> "postgres");
        registry.add("nexus.iam.jdbc.password", () -> "postgres");
    }

    private static MockHttpServletRequestBuilder platform(MockHttpServletRequestBuilder request) {
        return request.header(DemoPlatformContextFilter.ACTOR_HEADER, PLATFORM_ACTOR)
                .header(DemoPlatformContextFilter.REASON_HEADER, "i7-published-template-versions");
    }

    @Test
    void seededAndNewlyPublishedTemplatesExposeRunningVersionIds() throws Exception {
        String seeded = mvc.perform(platform(get("/api/v1/p1/platform/form-templates")))
                .andReturn().getResponse().getContentAsString();
        List<String> seededVersions = JsonPath.read(seeded,
                "$[?(@.code == 'demo_simple_approval' && @.status == 'PUBLISHED')].running_version_id");
        assertEquals(1, seededVersions.size(), seeded);
        assertFalse(seededVersions.getFirst() == null || seededVersions.getFirst().isBlank(), seeded);
        assertTrue(JsonPath.<List<Object>>read(seeded, "$[*].runningVersionId").isEmpty(), "wire format is snake_case");

        MvcResult created = mvc.perform(platform(post("/api/v1/p1/platform/form-templates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"i7_leave","name":"I7 请假","description":null,
                                 "fields":[{"key":"reason","label":"事由","type":"TEXT","required":true,"options":null},
                                           {"key":"approver","label":"审批人","type":"PERSON","required":true,"options":null}]}"""))
                .andReturn();
        assertTrue(created.getResponse().getStatus() < 300, created.getResponse().getContentAsString());
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        MvcResult validated = mvc.perform(platform(post("/api/v1/p1/platform/form-templates/" + id + "/validate"))).andReturn();
        assertEquals(200, validated.getResponse().getStatus(), validated.getResponse().getContentAsString());
        assertEquals(Boolean.TRUE, JsonPath.read(validated.getResponse().getContentAsString(), "$.valid"));

        MvcResult published = mvc.perform(platform(post("/api/v1/p1/platform/form-templates/" + id + "/publish"))).andReturn();
        assertEquals(200, published.getResponse().getStatus(), published.getResponse().getContentAsString());

        String listed = mvc.perform(platform(get("/api/v1/p1/platform/form-templates")))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> fresh = JsonPath.read(listed, "$[?(@.code == 'i7_leave')]");
        assertEquals(1, fresh.size(), listed);
        assertEquals("PUBLISHED", fresh.getFirst().get("status"));
        String versionId = (String) fresh.getFirst().get("running_version_id");
        assertTrue(versionId != null && !versionId.isBlank(), listed);

        // The page then saves a product definition (snake_case body, H8) and binds a SKU to it.
        MvcResult definition = mvc.perform(platform(post("/api/v1/p4/product-definitions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"i7_leave_product","name":"I7 请假产品","description":null,
                                 "form_template_version_id":"%s","process_definition_key":"%s","process_definition_version":1,
                                 "default_permission_pack":{"summary":"i7"}}""".formatted(
                                versionId, ApprovalProductDemoSeedConfiguration.PROCESS_KEY)))
                .andReturn();
        assertEquals(201, definition.getResponse().getStatus(), definition.getResponse().getContentAsString());
        String definitionId = JsonPath.read(definition.getResponse().getContentAsString(), "$.id");
        assertEquals(versionId, JsonPath.read(definition.getResponse().getContentAsString(), "$.form_template_version_id"));

        MvcResult bound = mvc.perform(platform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/p4/product-definitions/" + definitionId + "/skus/"
                                + ApprovalProductDemoSeedConfiguration.SKU_CODE)))
                .andReturn();
        assertEquals(200, bound.getResponse().getStatus(), bound.getResponse().getContentAsString());
        String catalog = mvc.perform(get("/api/v1/p4/catalog")).andReturn().getResponse().getContentAsString();
        List<String> boundTo = JsonPath.read(catalog, "$..skus[?(@.code == '"
                + ApprovalProductDemoSeedConfiguration.SKU_CODE + "')].product_definition.id");
        assertEquals(List.of(definitionId), boundTo, catalog);

        // Platform-scope audit rows are recorded under the operator's platform tenant, never NULL.
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM platform.audit_event
                 WHERE event_type LIKE 'platform.%' AND tenant_id IS NULL""", Integer.class));
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM platform.audit_event
                 WHERE event_type IN ('platform.form_template.created', 'platform.form_template.validated',
                                      'platform.form_template.published', 'platform.product_definition.created',
                                      'platform.product_definition.sku_bound')""", Integer.class) >= 5);
    }
}
