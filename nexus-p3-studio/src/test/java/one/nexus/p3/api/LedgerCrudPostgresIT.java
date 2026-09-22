package one.nexus.p3.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p3.P3StudioTestApplication;
import one.nexus.p3.PostgresAvailable;
import one.nexus.p3.infra.isolation.P3IsolationFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = P3StudioTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PostgresAvailable.class)
class LedgerCrudPostgresIT {
    private static final UUID ALPHA = UUID.fromString("0199a1c0-0000-7000-8000-00000000aa01");
    private static final UUID BETA = UUID.fromString("0199a1c0-0000-7000-8000-00000000bb01");
    private static final UUID ACTOR_A = UUID.fromString("0199a1c0-0000-7000-8000-00000000ac0a");
    private static final UUID ACTOR_B = UUID.fromString("0199a1c0-0000-7000-8000-00000000ac0b");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void crudListDetailPaginationAreTenantFilteredBeforeReturn() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        JsonNode app = createApp(ALPHA, "alpha", ACTOR_A, "crm" + suffix, "CRM");
        UUID appId = UUID.fromString(app.path("id").asText());
        JsonNode ledger = createLedger(ALPHA, "alpha", ACTOR_A, appId, "orders" + suffix);
        UUID ledgerId = UUID.fromString(ledger.path("id").asText());
        assertEquals("READY", ledger.path("status").asText());
        assertTrue(ledger.path("physical_table").asText().startsWith("l_alpha_"));

        JsonNode first = createRecord(ALPHA, "alpha", ACTOR_A, ledgerId, Map.of(
                "title", "alpha-one", "amount", 10, "owner_id", ACTOR_A.toString(), "department_id", "d1"));
        createRecord(ALPHA, "alpha", ACTOR_A, ledgerId, Map.of(
                "title", "alpha-two", "amount", 20, "owner_id", ACTOR_A.toString(), "department_id", "d1"));
        UUID recordId = UUID.fromString(first.path("id").asText());

        String page1 = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode page = mapper.readTree(page1);
        assertEquals(2, page.path("total").asInt());
        assertEquals(1, page.path("items").size());
        assertEquals(ALPHA.toString(), page.path("items").get(0).path("tenant_id").asText());

        String page2 = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode secondPage = mapper.readTree(page2);
        assertEquals(2, secondPage.path("total").asInt());
        assertEquals(1, secondPage.path("items").size());
        assertFalse(page.path("items").get(0).path("id").asText()
                .equals(secondPage.path("items").get(0).path("id").asText()));

        String queried = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .param("title", "alpha-two"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode filtered = mapper.readTree(queried);
        assertEquals(1, filtered.path("total").asInt());
        assertEquals("alpha-two", filtered.path("items").get(0).path("title").asText());

        String detail = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(ALPHA, "alpha", ACTOR_A)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals("alpha-one", mapper.readTree(detail).path("title").asText());

        long version = first.path("version").asLong();
        assertEquals(1, version);
        mvc.perform(patch("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "values", Map.of("title", "alpha-one-updated"),
                                "version", version))))
                .andExpect(status().isOk());
        String stale = mvc.perform(patch("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "values", Map.of("title", "stale"),
                                "version", version))))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(stale.contains("P3.RECORD.0002"), stale);

        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(BETA, "beta", ACTOR_B)))
                .andExpect(status().isNotFound());
        String betaList = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                        .headers(headers(BETA, "beta", ACTOR_B)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(betaList.contains("P3.LEDGER.0001") || betaList.contains("P3.LEDGER.0003"), betaList);

        mvc.perform(delete("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(ALPHA, "alpha", ACTOR_A)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .headers(headers(ALPHA, "alpha", ACTOR_A)))
                .andExpect(status().isNotFound());

        Integer leftover = jdbc.queryForObject(
                "select count(*) from business_data." + ledger.path("physical_table").asText()
                        + " where tenant_id = ? and deleted_at is null",
                Integer.class,
                ALPHA.toString());
        assertEquals(1, leftover);
    }

    @Test
    void illegalIdentifierIsRefusedBeforeDdl() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        JsonNode app = createApp(ALPHA, "alpha", ACTOR_A, "app" + suffix, "App");
        UUID appId = UUID.fromString(app.path("id").asText());
        String body = """
                {"ledger_code":"Bad-Name","display_name":"Bad","fields":[{"field_code":"title","display_name":"Title","field_type":"TEXT"}]}
                """;
        String response = mvc.perform(post("/api/v1/p3/applications/" + appId + "/ledgers")
                        .headers(headers(ALPHA, "alpha", ACTOR_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(response.contains("P3.SCHEMA.0001"), response);
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema='business_data' and table_name like 'l_alpha_bad%'",
                Integer.class);
        assertEquals(0, tables);
    }

    private JsonNode createApp(UUID tenant, String code, UUID actor, String appCode, String name) throws Exception {
        String body = mapper.writeValueAsString(Map.of("app_code", appCode, "display_name", name, "kind", "CUSTOM"));
        String json = mvc.perform(post("/api/v1/p3/applications")
                        .headers(headers(tenant, code, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode createLedger(UUID tenant, String code, UUID actor, UUID appId, String ledgerCode) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "ledger_code", ledgerCode,
                "display_name", "Orders",
                "scope", "TENANT",
                "fields", List.of(
                        Map.of("field_code", "title", "display_name", "Title", "field_type", "TEXT", "required", true, "indexed", true),
                        Map.of("field_code", "amount", "display_name", "Amount", "field_type", "NUMERIC", "required", false, "indexed", false))));
        String json = mvc.perform(post("/api/v1/p3/applications/" + appId + "/ledgers")
                        .headers(headers(tenant, code, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode createRecord(UUID tenant, String code, UUID actor, UUID ledgerId, Map<String, Object> values)
            throws Exception {
        String json = mvc.perform(post("/api/v1/p3/ledgers/" + ledgerId + "/records")
                        .headers(headers(tenant, code, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(values)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private static org.springframework.http.HttpHeaders headers(UUID tenant, String code, UUID actor) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, code);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "trace-" + tenant.toString().substring(0, 8));
        return headers;
    }
}
