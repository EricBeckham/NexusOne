package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.common.audit.AuditCategory;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * G4 leftover after #152: HTTP export-wizard complete deny (grant revoked
 * after begin) lands {@code p2.permission.denied} +
 * {@code isolation.guard.reject} on the JDBC hash chain and does not write
 * {@code p3.export.completed} / IsolatedExport submit. Same-TX {@code append}
 * is not redesigned. Does not reopen G18. Does not claim a product G4 close.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G4ExportWizardDenyIT {
    static final String SETUP_TRACE = "0199a1c0-0000-7000-8000-00000000c263";
    static final String BEGIN_TRACE = "0199a1c0-0000-7000-8000-00000000c264";
    static final String COMPLETE_DENY_TRACE = "0199a1c0-0000-7000-8000-00000000c265";
    static final String DECISION_TRACE = "0199a1c0-0000-7000-8000-00000000c266";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
    @Autowired IdentityModule identity;
    @Autowired JdbcTemplate jdbc;

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

    @AfterEach
    void clear() {
        one.nexus.p2.kernel.TenantContextHolder.clear();
        one.nexus.common.trace.TraceIds.clearMdc();
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
    void httpWizardCompleteDenyAfterBeginLandsOnJdbcChain() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String email = "g4-wizard-deny-" + suffix + "@example.com";
        UUID tenantId = createP1Tenant(suffix, email);
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantId, "G4 Wizard Deny Admin", email, "g4-wizard-deny-" + suffix));
        String token = StubIdentityProvider.token(
                "g4-wizard-deny-" + suffix, email, "g4-wizard-deny-" + suffix, "G4 Wizard Deny Admin");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contextId = extract(login.getResponse().getContentAsString(), "context_id");
        if (contextId == null || "null".equals(contextId)) {
            contextId = extract(login.getResponse().getContentAsString(), "contextId");
        }
        assertTrue(contextId != null && !"null".equals(contextId), login.getResponse().getContentAsString());

        UUID personId = identity.repository().findPersonByAccount(
                        tenantId,
                        identity.repository().findAccountByEmail(email).orElseThrow().id())
                .orElseThrow()
                .id();
        mvc.perform(post("/api/v1/permission/grants")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", BEGIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"GRANT\",\"principal_type\":\"user\",\"principal_id\":\""
                                + personId
                                + "\",\"resource\":\"ledger.export.csv\",\"action\":\"submit\",\"scope_kind\":\"SELF\"}"))
                .andExpect(status().isOk());

        MvcResult begun = mvc.perform(post("/api/v1/p3/exports/wizard/begin")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", BEGIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"{\\\"surface\\\":\\\"export.wizard\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("begin"))
                .andExpect(jsonPath("$.completed").value(false))
                .andReturn();
        String wizardId = extract(begun.getResponse().getContentAsString(), "wizard_id");
        assertTrue(wizardId != null && !"null".equals(wizardId), begun.getResponse().getContentAsString());
        assertEvent(BEGIN_TRACE, AuditCaptures.EXPORT_BEGUN, AuditCategory.EXPORT);

        mvc.perform(post("/api/v1/permission/grants")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", COMPLETE_DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"DENY\",\"principal_type\":\"user\",\"principal_id\":\""
                                + personId
                                + "\",\"resource\":\"ledger.export.csv\",\"action\":\"submit\",\"scope_kind\":\"SELF\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/p3/exports/wizard/complete")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", COMPLETE_DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wizard_id\":\"" + wizardId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0002"));

        List<ChainedAuditEvent> denied = audits.findByTraceId(COMPLETE_DENY_TRACE);
        assertTrue(denied.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing PERMISSION_DENIED for " + COMPLETE_DENY_TRACE + ": " + denied);
        assertTrue(denied.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing GUARD_REJECT for " + COMPLETE_DENY_TRACE + ": " + denied);
        assertTrue(denied.stream().noneMatch(event -> AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType())),
                () -> "complete-deny must not write EXPORT_COMPLETED: " + denied);
        assertTrue(denied.stream().noneMatch(event -> AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType())),
                () -> "complete-deny must not write IsolatedExport submit: " + denied);
        assertJdbc(COMPLETE_DENY_TRACE, AuditCaptures.PERMISSION_DENIED, 1);
        assertJdbc(COMPLETE_DENY_TRACE, AuditCaptures.EXPORT_COMPLETED, 0);
        assertJdbc(COMPLETE_DENY_TRACE, AuditCaptures.EXPORT_SUBMITTED, 0);

        mvc.perform(get("/api/v1/p3/exports/wizard/decision")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", DECISION_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.export.allowed").value(false))
                .andExpect(jsonPath("$.wizard").value(true));
        assertEquals(0, audits.findByTraceId(DECISION_TRACE).size(), "GET /decision must not write");

        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    private UUID createP1Tenant(String suffix, String email) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/p1/tenants")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-wizard-deny-setup")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, SETUP_TRACE)
                        .header("X-Trace-Id", SETUP_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G4 Wizard Deny " + suffix + "\",\"code\":\"g4d" + suffix
                                + "\",\"legal_name\":\"G4 Wizard Deny Ltd\",\"admin\":{\"display_name\":\"Admin "
                                + suffix + "\",\"email\":\"" + email + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");
        assertTrue(id != null && !"null".equals(id), created.getResponse().getContentAsString());
        return UUID.fromString(id);
    }

    private void assertEvent(String traceId, String eventType, AuditCategory category) {
        List<ChainedAuditEvent> found = audits.findByTraceId(traceId);
        assertTrue(found.stream().anyMatch(event ->
                        eventType.equals(event.event().eventType())
                                && event.event().category() == category),
                () -> "missing " + category + "/" + eventType + " on " + traceId + ": " + found);
        assertJdbc(traceId, eventType, 1);
    }

    private void assertJdbc(String traceId, String eventType, int minimum) {
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                traceId,
                eventType);
        if (minimum <= 0) {
            assertTrue(rows != null && rows == 0, "jdbc rows for " + eventType + " / " + traceId + " must be 0");
            return;
        }
        assertTrue(rows != null && rows >= minimum, "jdbc rows for " + eventType + " / " + traceId);
    }

    private static String extract(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
