package one.nexus.bootstrap;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.audit.AuditCategory;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.AuditEvent;
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
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G4 remainder: one product walk under a single {@code X-Trace-Id}.
 * Login → permission change → admin → accepted export → guard-reject.
 * Guard-reject uses existing {@code AuditPort.appendNewTransaction} survival
 * semantics (private commit; surrounding Spring TX rollback does not erase it).
 * Not a browser Authorization Code + PKCE walk. Does not change AuditPort TX
 * rules. Does not reopen product G18.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class G4SameTraceWalkIT {
    /**
     * Walk events share this id. Fixture tenant-create uses {@link #SETUP_TRACE}
     * so GET tenant-scope can include the admin config write on the same tenant.
     */
    static final String WALK_TRACE = "0199a1c0-0000-7000-8000-00000000c204";
    static final String SETUP_TRACE = "0199a1c0-0000-7000-8000-00000000c203";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static final String FOREIGN_TENANT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
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

    @AfterEach
    void clearIsolation() {
        AccessContextHolder.clear();
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
    void loginPermissionAdminExportAndGuardRejectShareOneTrace() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String email = "g4-walk-" + suffix + "@example.com";
        UUID tenantId = createP1TenantForWalk(suffix, email);
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantId, "G4 Walk Admin", email, "g4-walk-" + suffix));
        String token = StubIdentityProvider.token(
                "g4-walk-" + suffix, email, "g4-walk-" + suffix, "G4 Walk Admin");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", WALK_TRACE)
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
                        .header("X-Trace-Id", WALK_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"GRANT\",\"principal_type\":\"user\",\"principal_id\":\""
                                + personId
                                + "\",\"resource\":\"ledger\",\"action\":\"read\",\"scope_kind\":\"SELF\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/p1/tenants/" + tenantId + "/config")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-walk-admin")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, WALK_TRACE)
                        .header("X-Trace-Id", WALK_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"zh-CN\",\"timezone\":\"Asia/Shanghai\"}"))
                .andExpect(status().isOk());

        AtomicBoolean wrote = new AtomicBoolean(false);
        IsolationSupport.bindTenant(
                isolationKernel,
                tenantId.toString(),
                personId.toString(),
                contextId,
                WALK_TRACE);
        try {
            IsolatedExport.submit(tenantId.toString(), "{\"ledger\":\"leave\"}");
            IsolatedExport.writeFile(
                    tenantId.toString(),
                    "export/g4-walk.csv",
                    new byte[] {1, 2},
                    key -> wrote.set(true));
            IsolatedExport.dispatch(
                    IsolatedExport.submit(tenantId.toString(), "{}"),
                    tenantId.toString(),
                    item -> {});

            TransactionTemplate transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
            IsolationException reject = assertThrows(IsolationException.class, () -> transactions.execute(status -> {
                IsolatedExport.submit(FOREIGN_TENANT, "{}");
                return null;
            }));
            assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT.code(), reject.code());
            assertEquals(WALK_TRACE, reject.traceId());
        } finally {
            AccessContextHolder.clear();
        }
        assertTrue(wrote.get(), "accepted export file sink must run");

        List<ChainedAuditEvent> onPort = audits.findByTraceId(WALK_TRACE);
        assertEvent(onPort, AuditCaptures.LOGIN_ACCEPTED, AuditCategory.LOGIN);
        assertEvent(onPort, AuditCaptures.PERMISSION_GRANTED, AuditCategory.PERMISSION);
        assertEvent(onPort, AuditEvent.TENANT_CONFIG_UPDATED, AuditCategory.ADMIN);
        assertEvent(onPort, AuditCaptures.EXPORT_SUBMITTED, AuditCategory.EXPORT);
        assertEvent(onPort, AuditCaptures.EXPORT_FILE_WRITTEN, AuditCategory.EXPORT);
        assertEvent(onPort, AuditCaptures.GUARD_REJECT, AuditCategory.GUARD_REJECT);
        assertTrue(onPort.stream().allMatch(event -> WALK_TRACE.equals(event.event().traceId())));
        assertEquals(
                onPort.stream().map(ChainedAuditEvent::chainIndex).distinct().count(),
                onPort.size(),
                "walk rows must keep distinct chain indexes");
        assertTrue(
                onPort.stream().anyMatch(event ->
                        AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                                && "P1.M02.019".equals(event.event().resourceId())),
                () -> "guard-reject must be CROSS_TENANT_EXPORT: " + onPort);

        Integer surviveRows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                WALK_TRACE,
                AuditCaptures.GUARD_REJECT);
        assertTrue(surviveRows != null && surviveRows >= 1,
                "appendNewTransaction must keep GUARD_REJECT after the surrounding TX rolled back");

        mvc.perform(get("/api/v1/audit/traces/" + WALK_TRACE)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .header("X-Trace-Id", WALK_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value(WALK_TRACE))
                .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()))
                .andExpect(jsonPath("$.events[*].event_type").value(hasItems(
                        AuditCaptures.LOGIN_ACCEPTED,
                        AuditCaptures.PERMISSION_GRANTED,
                        AuditEvent.TENANT_CONFIG_UPDATED,
                        AuditCaptures.EXPORT_SUBMITTED,
                        AuditCaptures.GUARD_REJECT)))
                .andExpect(jsonPath("$.events[*].category").value(hasItems(
                        AuditCategory.LOGIN.name(),
                        AuditCategory.PERMISSION.name(),
                        AuditCategory.ADMIN.name(),
                        AuditCategory.EXPORT.name(),
                        AuditCategory.GUARD_REJECT.name())));

        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
        List<ChainedAuditEvent> setup = audits.findByTraceId(SETUP_TRACE);
        assertTrue(setup.stream().anyMatch(event ->
                        AuditEvent.TENANT_CREATED.equals(event.event().eventType())),
                () -> "linked SETUP_TRACE must keep the P1 tenant-create fixture: " + setup);
        assertTrue(onPort.stream().noneMatch(event ->
                        AuditEvent.TENANT_CREATED.equals(event.event().eventType())),
                "WALK_TRACE admin step is config-update so GET tenant-scope can include it");
    }

    private UUID createP1TenantForWalk(String suffix, String email) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/p1/tenants")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR)
                        .header(DemoPlatformContextFilter.REASON_HEADER, "g4-walk-setup")
                        .header(DemoPlatformContextFilter.TRACE_HEADER, SETUP_TRACE)
                        .header("X-Trace-Id", SETUP_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G4 Walk " + suffix + "\",\"code\":\"g4w" + suffix
                                + "\",\"legal_name\":\"G4 Walk Ltd\",\"admin\":{\"display_name\":\"Admin "
                                + suffix + "\",\"email\":\"" + email + "\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = extract(created.getResponse().getContentAsString(), "id");
        assertTrue(id != null && !"null".equals(id), created.getResponse().getContentAsString());
        return UUID.fromString(id);
    }

    private void assertEvent(List<ChainedAuditEvent> found, String eventType, AuditCategory category) {
        assertTrue(found.stream().anyMatch(event ->
                        eventType.equals(event.event().eventType())
                                && event.event().category() == category),
                () -> "missing " + category + "/" + eventType + " on " + WALK_TRACE + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                WALK_TRACE,
                eventType);
        assertTrue(rows != null && rows >= 1, "jdbc rows for " + eventType + " / " + WALK_TRACE);
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
