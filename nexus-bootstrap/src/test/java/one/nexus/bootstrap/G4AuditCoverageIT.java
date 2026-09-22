package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationException;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.AfterAll;
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
 * G4 coverage on the product {@link NexusOneApplication}: login, permission
 * change, admin, and guard-reject land on the existing JDBC hash chain and are
 * findable by trace. Same-TX rules stay {@code append} joins / {@code
 * appendNewTransaction} survives. Not G6 / G17.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G4AuditCoverageIT {
    private static final String LOGIN_TRACE = "0199a1c0-0000-7000-8000-00000000c104";
    private static final String REJECT_TRACE = "0199a1c0-0000-7000-8000-00000000c105";
    private static final String PERM_TRACE = "0199a1c0-0000-7000-8000-00000000c114";
    private static final String ADMIN_TRACE = "0199a1c0-0000-7000-8000-00000000c124";
    private static final String GUARD_TRACE = "0199a1c0-0000-7000-8000-00000000c134";
    private static final String SURVIVE_TRACE = "0199a1c0-0000-7000-8000-00000000c135";
    private static final String ROLLBACK_TRACE = "0199a1c0-0000-7000-8000-00000000c144";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
    @Autowired one.nexus.p1.port.AuditPort p1Audits;
    @Autowired IdentityModule identity;
    @Autowired IsolationKernel isolationKernel;
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
    void loginPermissionAdminAndGuardRejectLandOnChainByTrace() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G4 Admin", "g4-admin@example.com", "g4-admin"));
        String token = StubIdentityProvider.token("g4-admin", "g4-admin@example.com", "g4-admin", "G4 Admin");

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
        assertEvent(LOGIN_TRACE, AuditCaptures.LOGIN_ACCEPTED);

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", REJECT_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"not-a-stub-token\"}"))
                .andExpect(status().isUnauthorized());
        assertEvent(REJECT_TRACE, AuditCaptures.LOGIN_REJECTED);

        mvc.perform(post("/api/v1/permission/grants")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", PERM_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"GRANT\",\"principal_type\":\"user\",\"principal_id\":\""
                                + identity.repository().findPersonByAccount(TENANT,
                                identity.repository().findAccountByEmail("g4-admin@example.com").orElseThrow().id())
                                .orElseThrow().id()
                                + "\",\"resource\":\"ledger\",\"action\":\"read\",\"scope_kind\":\"SELF\"}"))
                .andExpect(status().isOk());
        assertEvent(PERM_TRACE, AuditCaptures.PERMISSION_GRANTED);

        String suffix = Long.toString(System.nanoTime(), 36);
        mvc.perform(post("/api/v1/p1/tenants")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-admin")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, ADMIN_TRACE)
                        .header("X-Trace-Id", ADMIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G4 " + suffix + "\",\"code\":\"g4" + suffix
                                + "\",\"legal_name\":\"G4 Ltd\",\"admin\":{\"display_name\":\"Admin "
                                + suffix + "\",\"email\":\"g4-" + suffix + "@example.com\"}}"))
                .andExpect(status().isCreated());
        assertEvent(ADMIN_TRACE, AuditEvent.TENANT_CREATED);

        mvc.perform(get("/api/v1/audit/traces/" + PERM_TRACE)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", PERM_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value(PERM_TRACE))
                .andExpect(jsonPath("$.events[0].event_type").value(AuditCaptures.PERMISSION_GRANTED));

        IsolationException reject = assertThrows(IsolationException.class, () ->
                isolationKernel.requests().bindTenant(null, TENANT.toString(), Optional.empty(), GUARD_TRACE));
        assertEquals("P1.M02.001", reject.code());
        assertEvent(GUARD_TRACE, AuditCaptures.GUARD_REJECT);

        TransactionTemplate transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        assertThrows(IsolationException.class, () -> transactions.execute(status -> {
            isolationKernel.requests().bindTenant(null, TENANT.toString(), Optional.empty(), SURVIVE_TRACE);
            return null;
        }));
        assertEvent(SURVIVE_TRACE, AuditCaptures.GUARD_REJECT);

        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            p1Audits.append(new AuditEvent(
                    UUID.fromString("0199a1c0-0000-7000-8000-00000000c145"),
                    AuditEvent.TENANT_ENABLED,
                    AuditEvent.VERSION,
                    Instant.parse("2026-09-22T15:30:00Z"),
                    TENANT,
                    TENANT,
                    TENANT,
                    true,
                    "rollback-probe",
                    ROLLBACK_TRACE,
                    ROLLBACK_TRACE,
                    null,
                    AuditEvent.OUTCOME_ACCEPTED,
                    "tenant",
                    TENANT.toString(),
                    Map.of("probe", "g4")));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, audits.findByTraceId(ROLLBACK_TRACE).size(),
                "append must join the ambient TX and roll back with it");
        Integer rolled = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ?",
                Integer.class,
                ROLLBACK_TRACE);
        assertEquals(0, rolled);

        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    private void assertEvent(String traceId, String eventType) {
        List<ChainedAuditEvent> found = audits.findByTraceId(traceId);
        assertTrue(found.stream().anyMatch(event -> eventType.equals(event.event().eventType())),
                () -> "missing " + eventType + " for " + traceId + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                traceId,
                eventType);
        assertTrue(rows != null && rows >= 1, "jdbc rows for " + eventType + " / " + traceId);
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
