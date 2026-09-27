package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.PrintMappingRevision;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingStore;
import one.nexus.p4.market.infra.jdbc.JdbcPrintMappingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DPO-L3: the application wires the JDBC print-mapping store on the V707 tables. A second store instance
 * (as after a restart) reads what the first saved; the conditional write refuses a stale revision; history
 * keeps every accepted save, newest first.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class PrintMappingPersistenceTest {
    private static EmbeddedPostgres postgres;

    @Autowired PrintMappingStore store;
    @Autowired JdbcClient jdbc;
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

    private UUID product() {
        UUID template = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-09-27T00:00:00Z"));
        jdbc.sql("INSERT INTO platform.form_template(id,code,name,status,created_at,updated_at,version) VALUES(:id,:code,'t','PUBLISHED',:now,:now,1)")
                .param("id", template).param("code", "dpo-l3-" + template).param("now", now).update();
        jdbc.sql("INSERT INTO platform.form_template_version(id,template_id,version,fields_snapshot,published_at) VALUES(:id,:t,1,'[]'::jsonb,:now)")
                .param("id", version).param("t", template).param("now", now).update();
        jdbc.sql("""
                INSERT INTO market.product_definition(id,code,name,form_template_version_id,default_permission_pack,status,created_at,updated_at,version)
                VALUES(:id,:code,'p',:v,'{}'::jsonb,'DRAFT',:now,:now,1)
                """).param("id", product).param("code", "dpo-l3-" + product).param("v", version).param("now", now).update();
        return product;
    }

    private static ProductPrintMapping mapping(UUID product, long revision, List<FieldSlotMapping> mappings) {
        return new ProductPrintMapping(product, ProductPrintMapping.SOURCE_STORED, "A4", "PORTRAIT",
                "builtin.approval-form.a4.v1", mappings, ProductPrintMapping.NOTE_STORED, revision);
    }

    @Test
    void savesSurviveANewStoreInstanceAndStaleRevisionsWriteNothing() {
        assertInstanceOf(JdbcPrintMappingStore.class, store);
        UUID product = product();
        UUID actor = UUID.randomUUID();
        List<FieldSlotMapping> first = List.of(new FieldSlotMapping("title", "form.fields"));
        List<FieldSlotMapping> second = List.of(new FieldSlotMapping("title", "header.application_no"));

        assertTrue(store.saveIfRevision(mapping(product, 1, first), 0, actor, Instant.parse("2026-09-27T01:00:00Z")));
        assertFalse(store.saveIfRevision(mapping(product, 1, second), 0, actor, Instant.now()), "second insert at r0 must lose");
        assertTrue(store.saveIfRevision(mapping(product, 2, second), 1, actor, Instant.parse("2026-09-27T02:00:00Z")));
        assertFalse(store.saveIfRevision(mapping(product, 3, first), 1, actor, Instant.now()), "stale r1 must not overwrite r2");

        PrintMappingStore afterRestart = new JdbcPrintMappingStore(jdbc);
        ProductPrintMapping stored = afterRestart.find(product).orElseThrow();
        assertEquals(2, stored.revision());
        assertEquals(second, stored.mappings());
        assertEquals(ProductPrintMapping.SOURCE_STORED, stored.source());

        List<PrintMappingRevision> history = afterRestart.history(product, 20);
        assertEquals(List.of(2L, 1L), history.stream().map(PrintMappingRevision::revision).toList());
        assertEquals(first, history.get(1).mappings());
        assertEquals(actor, history.get(0).savedBy());
        assertEquals(Instant.parse("2026-09-27T02:00:00Z"), history.get(0).savedAt());
        assertEquals(1, afterRestart.history(product, 1).size());
        assertTrue(afterRestart.find(product()).isEmpty());
    }

    @Test
    void mappingJsonUsesFixedKeysAndTheWireStaysSnakeCase() {
        UUID product = product();
        store.saveIfRevision(mapping(product, 1, List.of(new FieldSlotMapping("days", "form.fields"))), 0, null, Instant.now());
        String raw = jdbc.sql("SELECT mappings::text FROM market.print_mapping WHERE product_definition_id=:id")
                .param("id", product).query(String.class).single();
        assertTrue(raw.contains("\"field_key\"") && raw.contains("\"slot_id\""), raw);

        JsonNode wire = mapper.valueToTree(store.history(product, 5).get(0));
        assertEquals("days", wire.path("mappings").path(0).path("field_key").asText());
        assertTrue(wire.path("saved_at").isTextual());
        assertTrue(wire.path("saved_by").isMissingNode(), "no actor recorded is absent, not invented");
    }
}
