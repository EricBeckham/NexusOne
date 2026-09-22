package one.nexus.p3.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import one.nexus.isolation.IsolationKernel;
import one.nexus.p3.P3StudioTestApplication;
import one.nexus.p3.PostgresAvailable;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.infra.isolation.P3IsolationFilter;
import one.nexus.p3.infra.schema.PostgresDialectRenderer;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.ScopedQuery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = P3StudioTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PostgresAvailable.class)
class SchemaServicePostgresIT {
    private static final UUID TENANT = UUID.fromString("0199a1c0-0000-7000-8000-00000000aa02");
    private static final UUID ACTOR = UUID.fromString("0199a1c0-0000-7000-8000-00000000ac02");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IsolationKernel kernel;

    @Autowired
    SchemaService schemaService;

    @Test
    void createEvolveRegistersTableAndRefusesIllegalIdentifiers() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String appJson = mvc.perform(post("/api/v1/p3/applications")
                        .headers(headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "app_code", "pkg" + suffix, "display_name", "Pkg", "kind", "CUSTOM"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID appId = UUID.fromString(mapper.readTree(appJson).path("id").asText());
        String ledgerJson = mvc.perform(post("/api/v1/p3/applications/" + appId + "/ledgers")
                        .headers(headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "ledger_code", "ticket" + suffix,
                                "display_name", "Tickets",
                                "fields", List.of(Map.of(
                                        "field_code", "title",
                                        "display_name", "Title",
                                        "field_type", "TEXT",
                                        "required", true,
                                        "indexed", true))))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ledger = mapper.readTree(ledgerJson);
        String table = ledger.path("physical_table").asText();
        assertTrue(table.startsWith("l_alpha_ticket"));
        Integer physical = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema='business_data' and table_name=?",
                Integer.class,
                table);
        assertEquals(1, physical);
        Integer flywayLedger = jdbc.queryForObject(
                "select count(*) from platform.flyway_schema_history where script ilike '%create table l_%'",
                Integer.class);
        assertEquals(0, flywayLedger);
        Integer registered = jdbc.queryForObject(
                "select count(*) from platform.table_registry where table_schema='business_data' and table_name=?",
                Integer.class,
                table);
        assertEquals(1, registered);
        assertTrue(kernel.registry().snapshot().containsKey(table));
        assertTrue(kernel.registry().require(table).columns().contains("owner_id"));

        String evolved = mvc.perform(post("/api/v1/p3/ledgers/" + ledger.path("id").asText() + "/fields")
                        .headers(headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "field_code", "priority",
                                "display_name", "Priority",
                                "field_type", "INTEGER",
                                "required", false,
                                "indexed", true))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(2, mapper.readTree(evolved).path("schema_version").asInt());
        Integer column = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema='business_data' and table_name=? and column_name='priority'",
                Integer.class,
                table);
        assertEquals(1, column);
        Integer idx = jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname='business_data' and tablename=? and indexname=?",
                Integer.class,
                table,
                table + "_priority_idx");
        assertEquals(1, idx);
        assertTrue(kernel.registry().require(table).columns().contains("priority"));

        assertThrows(P3Exception.class, () -> PostgresDialectRenderer.quoteIdent("Bad"));
        assertThrows(P3Exception.class, () -> schemaService.astOf(
                new one.nexus.p3.domain.LedgerDefinition(
                        UUID.randomUUID(), TENANT, "alpha", appId, "x", "X",
                        one.nexus.p3.domain.LedgerScope.TENANT, "not_a_ledger", 1,
                        one.nexus.p3.domain.LedgerStatus.DRAFT, java.time.Instant.now(), "a",
                        java.time.Instant.now(), "a", null, 1),
                List.of()));
    }

    @Test
    void dataScopeListDetailShareTenantFirstWhere() {
        ScopedQuery scoped = PermissionQueryBinder.bind(
                TENANT.toString(), new Decision(true, "self", Predicate.atom("owner_id", "=", ACTOR.toString()), Map.of(), 1L));
        assertTrue(scoped.whereSql().startsWith("tenant_id = ? AND "));
        var count = scoped.count("l_alpha_orders");
        var list = scoped.list("l_alpha_orders", List.of("id", "title"), "id", 10, 0);
        var detail = scoped.detail("l_alpha_orders", List.of("id", "title"), "id", "x");
        assertEquals(
                count.sql().substring(count.sql().indexOf("WHERE")),
                list.sql().substring(list.sql().indexOf("WHERE"), list.sql().indexOf(" ORDER")));
        assertEquals(count.parameters(), list.parameters().subList(0, count.parameters().size()));
        assertEquals(count.parameters(), detail.parameters().subList(0, count.parameters().size()));
    }

    private static org.springframework.http.HttpHeaders headers() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, TENANT.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, "alpha");
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, ACTOR.toString());
        headers.add("X-Trace-Id", "trace-schema");
        return headers;
    }
}
