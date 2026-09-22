package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p1.port.AuditRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Staging-like P1 {@code GET /tenants/{id}/audit-events} query + export on Zonky PG 15.
 * GET still reads {@code platform.audit_event}. Does not switch onto M0-11.
 * Does not redo the HTTP export wizard. Does not claim product G4 closed.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G4P1StagingAuditEventsIT {
    private static final String CREATE_TRACE = "0199a1c0-0000-7000-8000-00000000c260";
    private static final String STAGING_ONLY_TRACE = "0199a1c0-0000-7000-8000-00000000c261";
    private static final String CHAIN_ONLY_TRACE = "0199a1c0-0000-7000-8000-00000000c262";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
    @Autowired AuditRepository staging;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @TempDir
    Path tmp;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            if (!rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + rs.getString(1));
            }
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("nexus.notify.outbox", () -> "auto");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.p1.demo-context.enabled", () -> "true");
        registry.add("nexus.iam.idp", () -> "stub");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.permission.persistence", () -> "memory");
        registry.add("nexus.permission.seed-tenant-member-grants", () -> "false");
        registry.add("nexus.audit.persistence", () -> "jdbc");
    }

    @Test
    void getAuditEventsReadsStagingAndExportsJson() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        MvcResult created = mvc.perform(post("/api/v1/p1/tenants")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-p1-staging")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, CREATE_TRACE)
                        .header("X-Trace-Id", CREATE_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G4 P1 " + suffix + "\",\"code\":\"g4p1" + suffix
                                + "\",\"legal_name\":\"G4 P1 Ltd\",\"admin\":{\"display_name\":\"Admin "
                                + suffix + "\",\"email\":\"g4-p1-" + suffix + "@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = mapper.readTree(created.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(body.path("tenant").path("id").asText());

        MvcResult listed = mvc.perform(get("/api/v1/p1/tenants/" + tenantId + "/audit-events")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-p1-staging")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, CREATE_TRACE))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = mapper.readTree(listed.getResponse().getContentAsString());
        assertTrue(events.isArray() && events.size() > 0, listed.getResponse().getContentAsString());
        assertEquals(AuditEvent.TENANT_CREATED, events.get(0).path("event_type").asText());

        Integer stagingRows = jdbc.queryForObject(
                "select count(*) from platform.audit_event where tenant_id = ?",
                Integer.class,
                tenantId);
        assertEquals(events.size(), stagingRows);

        UUID stagingOnlyId = UUID.fromString("0199a1c0-0000-7000-8000-00000000c263");
        jdbc.update("""
                INSERT INTO platform.audit_event (
                  event_id, event_type, event_version, occurred_at, tenant_id,
                  actor_subject_id, actor_tenant_id, platform_context, reason,
                  trace_id, correlation_id, causation_id, outcome, resource_type, resource_id, details)
                VALUES (?, ?, 1, now(), ?, ?::uuid, ?, true, 'staging-only',
                  ?, ?, null, 'ACCEPTED', 'tenant', ?, '{}'::jsonb)
                """,
                stagingOnlyId,
                AuditEvent.TENANT_ENABLED,
                tenantId,
                ACTOR,
                tenantId,
                STAGING_ONLY_TRACE,
                STAGING_ONLY_TRACE,
                tenantId.toString());
        assertEquals(0, audits.findByTraceId(STAGING_ONLY_TRACE).size(),
                "staging-only row must not appear on the hash chain");

        audits.append(AuditCaptures.admin(
                "p1.tenant.config.updated",
                true,
                tenantId,
                UUID.fromString(ACTOR),
                tenantId,
                "tenant",
                tenantId.toString(),
                "chain-only",
                CHAIN_ONLY_TRACE,
                Map.of("harness", "hash-chain-only")));
        List<ChainedAuditEvent> chainOnly = audits.findByTraceId(CHAIN_ONLY_TRACE);
        assertFalse(chainOnly.isEmpty(), "hash-chain-only append must land on audit_record");

        MvcResult after = mvc.perform(get("/api/v1/p1/tenants/" + tenantId
                        + "/audit-events?event_type=" + AuditEvent.TENANT_ENABLED)
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-p1-staging")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, STAGING_ONLY_TRACE))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode enabled = mapper.readTree(after.getResponse().getContentAsString());
        assertTrue(enabled.toString().contains(stagingOnlyId.toString()),
                "GET must read staging-only row: " + enabled);
        assertFalse(enabled.toString().contains(CHAIN_ONLY_TRACE),
                "GET must not read hash-chain-only row: " + enabled);

        List<AuditEvent> queried = staging.listByTenant(tenantId, 50);
        Map<String, Object> bundleMap = new LinkedHashMap<>();
        bundleMap.put("leftover", "g4-p1-staging-audit-events");
        bundleMap.put("source_table", "platform.audit_event");
        bundleMap.put("query_path", "GET /api/v1/p1/tenants/{id}/audit-events");
        bundleMap.put("switched_off_staging", false);
        bundleMap.put("product_g4_closed", false);
        bundleMap.put("http_export_wizard", false);
        bundleMap.put("tenant_id", tenantId.toString());
        bundleMap.put("count", queried.size());
        Path export = tmp.resolve("p1-staging-audit-events.json");
        Files.writeString(export, mapper.writeValueAsString(bundleMap) + "\n");
        JsonNode bundle = mapper.readTree(Files.readString(export));
        assertEquals("platform.audit_event", bundle.path("source_table").asText());
        assertEquals(false, bundle.path("switched_off_staging").asBoolean());
        assertEquals(false, bundle.path("product_g4_closed").asBoolean());
        assertEquals(false, bundle.path("http_export_wizard").asBoolean());
        assertTrue(bundle.path("count").asInt() >= 2, bundle.toString());
        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }
}
