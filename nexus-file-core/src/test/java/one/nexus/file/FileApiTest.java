package one.nexus.file;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import one.nexus.file.api.FileIsolationFilter;
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

@SpringBootTest(classes = FileApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "nexus.files.demo-context=true",
        "nexus.files.persistence=memory",
        "nexus.files.storage=memory",
        "nexus.cache.mode=memory",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class FileApiTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void signedUrlRejectsCrossTenantBeforeS3() throws Exception {
        String payload = mapper.writeValueAsString(new Upload(
                "ledger",
                "note.txt",
                "text/plain",
                Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)),
                "rec-1"));
        MvcResult created = mvc.perform(post("/api/v1/files")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.object_key").value(org.hamcrest.Matchers.startsWith("tenants/" + TENANT_A + "/")))
                .andReturn();
        JsonNode body = mapper.readTree(created.getResponse().getContentAsString());
        UUID fileId = UUID.fromString(body.get("file_id").asText());

        mvc.perform(post("/api/v1/files/" + fileId + "/signed-url")
                        .header(FileIsolationFilter.HEADER_DEMO_TENANT, TENANT_B)
                        .header(FileIsolationFilter.HEADER_DEMO_ACTOR, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"user-a\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P1.M02.015"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {"one.nexus.file", "one.nexus.cache"})
    static class TestApp {}

    record Upload(String businessType, String fileName, String contentType, String contentBase64, String resourceId) {}
}
