package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.common.trace.TraceIds;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G4 leftover: SurfaceGuard deny on product form / report HTTP lands
 * {@code p2.permission.denied} + {@code isolation.guard.reject} on the JDBC
 * hash chain via {@code appendNewTransaction}. Same-TX {@code append} is not
 * redesigned. Does not reopen G18. Does not claim a product G4 close.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G4GuardDenyAuditIT {
    private static final String SETUP_TRACE = "0199a1c0-0000-7000-8000-00000000c237";
    private static final String FORM_TRACE = "0199a1c0-0000-7000-8000-00000000c238";
    private static final String QUERY_TRACE = "0199a1c0-0000-7000-8000-00000000c239";
    private static final String EXPORT_TRACE = "0199a1c0-0000-7000-8000-00000000c23a";
    private static final String DECISION_TRACE = "0199a1c0-0000-7000-8000-00000000c23b";
    private static final String SURVIVE_TRACE = "0199a1c0-0000-7000-8000-00000000c23c";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
    @Autowired PermissionModule permission;
    @Autowired IdentityModule identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

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
        TenantContextHolder.clear();
        TraceIds.clearMdc();
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
    void formAndReportDenyLandOnJdbcChainAndSurviveRollback() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String email = "g4-deny-" + suffix + "@example.com";
        UUID tenantId = createP1Tenant(suffix, email);
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantId, "G4 Deny Admin", email, "g4-deny-" + suffix));
        String token = StubIdentityProvider.token(
                "g4-deny-" + suffix, email, "g4-deny-" + suffix, "G4 Deny Admin");

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

        mvc.perform(get("/api/v1/p3/forms")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", FORM_TRACE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        assertDenied(FORM_TRACE, "form", "read");

        mvc.perform(post("/api/v1/p3/reports/query")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", QUERY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledger_id\":\"0199a1c0-0000-7000-8000-00000000c23d\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        assertDenied(QUERY_TRACE, "ledger", "read");

        mvc.perform(post("/api/v1/p3/reports/export")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", EXPORT_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        assertDenied(EXPORT_TRACE, "ledger.export.csv", "submit");

        mvc.perform(get("/api/v1/p3/reports/decision")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", DECISION_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.allowed").value(false));
        assertEquals(0, audits.findByTraceId(DECISION_TRACE).size(),
                "GET /decision must not write deny");

        UUID accountId = identity.repository().findAccountByEmail(email).orElseThrow().id();
        UUID personId = identity.repository().findPersonByAccount(tenantId, accountId).orElseThrow().id();
        TenantContextHolder.set(new TenantContext(
                UUID.fromString(contextId), tenantId, accountId, personId, "g4-deny",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)));
        TraceIds.putMdc(SURVIVE_TRACE);
        TransactionTemplate transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        assertThrows(PermissionException.class, () -> transactions.execute(status -> {
            SurfaceGuard.require(permission, "form", "write");
            return null;
        }));
        assertDenied(SURVIVE_TRACE, "form", "write");

        mvc.perform(get("/api/v1/audit/traces/" + FORM_TRACE)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", FORM_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value(FORM_TRACE))
                .andExpect(jsonPath("$.events[*].event_type").value(hasItems(
                        AuditCaptures.PERMISSION_DENIED, AuditCaptures.GUARD_REJECT)));

        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    private UUID createP1Tenant(String suffix, String email) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/p1/tenants")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-deny-setup")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, SETUP_TRACE)
                        .header("X-Trace-Id", SETUP_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G4 Deny " + suffix + "\",\"code\":\"g4d" + suffix
                                + "\",\"legal_name\":\"G4 Deny Ltd\",\"admin\":{\"display_name\":\"Admin "
                                + suffix + "\",\"email\":\"" + email + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");
        assertTrue(id != null && !"null".equals(id), created.getResponse().getContentAsString());
        return UUID.fromString(id);
    }

    private void assertDenied(String traceId, String resource, String action) {
        List<ChainedAuditEvent> found = audits.findByTraceId(traceId);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing PERMISSION_DENIED for " + traceId + ": " + found);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing GUARD_REJECT for " + traceId + ": " + found);
        assertTrue(found.stream().anyMatch(event ->
                        resource.equals(event.event().details().get("resource"))
                                && action.equals(event.event().details().get("action"))),
                () -> "missing " + resource + "." + action + " on " + traceId + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                traceId,
                AuditCaptures.PERMISSION_DENIED);
        assertTrue(rows != null && rows >= 1, "jdbc rows for PERMISSION_DENIED / " + traceId);
        Integer guardRows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                traceId,
                AuditCaptures.GUARD_REJECT);
        assertTrue(guardRows != null && guardRows >= 1, "jdbc rows for GUARD_REJECT / " + traceId);
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
