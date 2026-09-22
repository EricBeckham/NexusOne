package one.nexus.file;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import one.nexus.file.api.FileIsolationFilter;
import one.nexus.file.infra.CountingObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FileReconcileApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "nexus.files.demo-context=true",
        "nexus.files.persistence=memory",
        "nexus.files.storage=memory",
        "nexus.files.platform-bootstrap-token=test-bootstrap",
        "nexus.cache.mode=memory",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class FileReconcileApiTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ObjectStoragePort storage;

    @Test
    void tenantPreviewAndApplyMarkMissing() throws Exception {
        UUID fileId = upload(TENANT_A, "user-a", "note.txt", "hello");
        String objectKey = ((CountingObjectStorage) storage).listKeys("nexus-files", "tenants/" + TENANT_A + "/")
                .getFirst();
        ((CountingObjectStorage) storage).dropQuietly("nexus-files", objectKey);

        mvc.perform(get("/api/v1/files/reconcile")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("files.reconcile"))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.missing").value(1))
                .andExpect(jsonPath("$.repaired").value(0));

        mvc.perform(post("/api/v1/files/reconcile")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apply\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.repaired").value(1))
                .andExpect(jsonPath("$.repairs[0].action").value("MARK_MISSING"));

        mvc.perform(get("/api/v1/files/" + fileId)
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CORE.FILE.0005"));
    }

    @Test
    void tenantBDoesNotSeeTenantAOrphan() throws Exception {
        String orphanA = "tenants/" + TENANT_A + "/ledger/2026/09/api-orphan";
        storage.put("nexus-files", orphanA, "keep".getBytes(StandardCharsets.UTF_8), "text/plain", -1);

        mvc.perform(post("/api/v1/files/reconcile")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_B)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apply\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orphan").value(0))
                .andExpect(jsonPath("$.repaired").value(0));
        org.junit.jupiter.api.Assertions.assertTrue(storage.exists("nexus-files", orphanA));
    }

    @Test
    void internalPlatformRequiresBootstrapToken() throws Exception {
        mvc.perform(get("/api/v1/internal/files/reconcile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/files/reconcile")
                        .header(FileIsolationFilter.HEADER_BOOTSTRAP, "test-bootstrap")
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "ops-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("files.reconcile"))
                .andExpect(jsonPath("$.applied").value(false));
    }

    private UUID upload(String tenant, String actor, String name, String body) throws Exception {
        String payload = mapper.writeValueAsString(new Upload(
                "ledger",
                name,
                "text/plain",
                Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)),
                "rec-1"));
        MvcResult created = mvc.perform(post("/api/v1/files")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, tenant)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = mapper.readTree(created.getResponse().getContentAsString());
        return UUID.fromString(json.get("file_id").asText());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {"one.nexus.file", "one.nexus.cache"})
    static class TestApp {}

    record Upload(String businessType, String fileName, String contentType, String contentBase64, String resourceId) {}
}
