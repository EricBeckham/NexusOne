package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.permission.admin.PlatformDutyCatalog;
import org.junit.jupiter.api.AfterAll;
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
 * G17 slice on the product {@link NexusOneApplication}: audit-admin can read
 * traces; write / delete / admin-of-audit 403 and land on the existing hash
 * chain. Assign of a second duty is rejected. Not a full G17 close.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G17AuditAdminIT {
    private static final String LOGIN_TRACE = "0199a1c0-0000-7000-8000-00000000d201";
    private static final String DELETE_TRACE = "0199a1c0-0000-7000-8000-00000000d202";
    private static final String CONFIG_TRACE = "0199a1c0-0000-7000-8000-00000000d203";
    private static final String ADMIN_TRACE = "0199a1c0-0000-7000-8000-00000000d204";
    private static final UUID TENANT = UUID.fromString("17171717-1717-4717-8717-171717171720");
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
        registry.add("nexus.p1.demo-context.enabled", () -> "false");
        registry.add("nexus.iam.idp", () -> "stub");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.permission.persistence", () -> "memory");
        registry.add("nexus.permission.seed-tenant-member-grants", () -> "false");
        registry.add("nexus.audit.persistence", () -> "jdbc");
    }

    @Test
    void auditAdminReadsTracesAndCannotMutateChain() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G17 Admin", "g17-admin@example.com", "g17-admin"));
        var person = identity.repository().findPersonByAccount(TENANT,
                identity.repository().findAccountByEmail("g17-admin@example.com").orElseThrow().id()).orElseThrow();
        String token = StubIdentityProvider.token("g17-admin", "g17-admin@example.com", "g17-admin", "G17 Admin");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", LOGIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contextId = extract(login.getResponse().getContentAsString(), "context_id");
        if (contextId == null || "null".equals(contextId)) {
            contextId = extract(login.getResponse().getContentAsString(), "contextId");
        }
        assertTrue(contextId != null && !"null".equals(contextId), login.getResponse().getContentAsString());

        mvc.perform(post("/api/v1/permission/duties/example")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog_installed").value(true));

        String auditRoleId = extractRoleId(mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andReturn()
                .getResponse()
                .getContentAsString(), PlatformDutyCatalog.AUDIT_ADMIN);
        String systemRoleId = extractRoleId(mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andReturn()
                .getResponse()
                .getContentAsString(), PlatformDutyCatalog.SYSTEM_ADMIN);

        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal_type\":\"user\",\"principal_id\":\"" + person.id()
                                + "\",\"role_id\":\"" + auditRoleId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/audit/traces/" + LOGIN_TRACE)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value(LOGIN_TRACE))
                .andExpect(jsonPath("$.events[0].event_type").value(AuditCaptures.LOGIN_ACCEPTED));

        mvc.perform(delete("/api/v1/audit/traces/" + LOGIN_TRACE)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", DELETE_TRACE))
                .andExpect(status().isForbidden());
        assertDeniedOnChain(DELETE_TRACE);

        mvc.perform(put("/api/v1/audit/config")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", CONFIG_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retention_days\":1}"))
                .andExpect(status().isForbidden());
        assertDeniedOnChain(CONFIG_TRACE);

        mvc.perform(post("/api/v1/audit/admin")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", ADMIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"op\":\"mute\"}"))
                .andExpect(status().isForbidden());
        assertDeniedOnChain(ADMIN_TRACE);

        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal_type\":\"user\",\"principal_id\":\"" + person.id()
                                + "\",\"role_id\":\"" + systemRoleId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P2.PERM.0014"));

        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
        assertTrue(audits.findByTraceId(LOGIN_TRACE).stream()
                        .anyMatch(event -> AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())),
                "login event must remain on the hash chain after refused delete");
        Integer remaining = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                LOGIN_TRACE,
                AuditCaptures.LOGIN_ACCEPTED);
        assertTrue(remaining != null && remaining >= 1, "login event must remain on the chain");
    }

    private void assertDeniedOnChain(String traceId) {
        List<ChainedAuditEvent> found = audits.findByTraceId(traceId);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing permission-denied for " + traceId + ": " + found);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing guard-reject for " + traceId + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ?",
                Integer.class,
                traceId);
        assertTrue(rows != null && rows >= 2, "jdbc rows for deny / " + traceId);
    }

    private static String extractRoleId(String json, String code) {
        int codeAt = json.indexOf("\"code\":\"" + code + "\"");
        if (codeAt < 0) {
            throw new AssertionError("role " + code + " missing in " + json);
        }
        int windowStart = Math.max(0, codeAt - 180);
        String window = json.substring(windowStart, Math.min(json.length(), codeAt + 80));
        String needle = "\"id\":\"";
        int start = window.lastIndexOf(needle);
        if (start < 0) {
            start = window.indexOf(needle);
        }
        int from = start + needle.length();
        return window.substring(from, window.indexOf("\"", from));
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
