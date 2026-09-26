package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p3.flow.ProcessDefinitionValidationResult;
import one.nexus.p3.flow.ProcessDefinitionValidationResult.ValidationError;
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
 * ZA14: POST /api/v1/process/drafts/{key}/validate returns ProcessDefinitionValidationResult through the
 * product's configured mapper (SNAKE_CASE, non_null). Pins the wire names the web designer reads for the
 * ZA11 pairing locators: node_id / flow_id, absent (not null) when a finding has no location.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class ProcessDraftValidateWireNamingTest {
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
    void pairingFindingsUseSnakeCaseLocatorsAndOmitMissingOnes() {
        JsonNode body = mapper.valueToTree(new ProcessDefinitionValidationResult(false, List.of(
                new ValidationError("PARALLEL_BRANCH_NOT_JOINED", "并行分支没有汇合就结束了", "fork", "fa"),
                new ValidationError("PARALLEL_JOIN_UNPAIRED", "并行汇合没有对应的并行开始", "join", null),
                new ValidationError("END_REQUIRED", "流程至少需要一个结束节点", null, null))));

        assertFalse(body.path("valid").asBoolean(true));
        JsonNode located = body.path("errors").get(0);
        assertEquals("PARALLEL_BRANCH_NOT_JOINED", located.path("code").asText());
        assertEquals("fork", located.path("node_id").asText());
        assertEquals("fa", located.path("flow_id").asText());
        assertTrue(located.path("nodeId").isMissingNode(), "wire format is snake_case");
        assertTrue(located.path("flowId").isMissingNode(), "wire format is snake_case");

        JsonNode nodeOnly = body.path("errors").get(1);
        assertEquals("join", nodeOnly.path("node_id").asText());
        assertTrue(nodeOnly.path("flow_id").isMissingNode(), "non_null inclusion drops the absent flow locator");

        JsonNode formLevel = body.path("errors").get(2);
        assertTrue(formLevel.path("node_id").isMissingNode() && formLevel.path("flow_id").isMissingNode());

        JsonNode ok = mapper.valueToTree(new ProcessDefinitionValidationResult(true, List.of()));
        assertTrue(ok.path("valid").asBoolean(false));
        assertEquals(0, ok.path("errors").size());
    }
}