package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p4.market.PrintTemplateModels;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;
import one.nexus.p4.market.PrintTemplateStore;
import one.nexus.p4.market.infra.jdbc.JdbcPrintTemplateStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** DPO-L4: copies persist in market.print_template (V708); the table refuses built-in codes; wire is snake_case. */
@SpringBootTest(classes = NexusOneApplication.class)
class PrintTemplatePersistenceTest {
    private static EmbeddedPostgres postgres;

    @Autowired PrintTemplateStore store;
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

    private static PrintTemplate copy(String code) {
        return new PrintTemplate(UUID.randomUUID(), code, "副本", PrintTemplateModels.KIND_COPY,
                "builtin.approval-form.a4.v1", "A5", "LANDSCAPE", true, Instant.parse("2026-09-27T00:00:00Z"),
                null, 1, PrintTemplateModels.NOTE_COPY);
    }

    @Test
    void copiesPersistAndBuiltinCodesAreRefusedByTheTable() {
        assertInstanceOf(JdbcPrintTemplateStore.class, store);
        store.insert(copy("copy.dpol4test01"));
        PrintTemplate stored = store.copies().stream().filter(t -> t.code().equals("copy.dpol4test01")).findFirst().orElseThrow();
        assertEquals("A5", stored.paper());
        assertEquals("COPY", stored.kind());
        assertTrue(stored.editable());
        assertThrows(DataIntegrityViolationException.class, () -> store.insert(copy("builtin.approval-form.a4.v1")));

        JsonNode wire = mapper.valueToTree(stored);
        assertEquals("builtin.approval-form.a4.v1", wire.path("source_template_code").asText());
        assertTrue(wire.path("created_at").isTextual());
        assertTrue(wire.path("created_by").isMissingNode());
    }
}
