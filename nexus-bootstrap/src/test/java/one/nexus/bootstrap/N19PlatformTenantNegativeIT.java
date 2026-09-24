package one.nexus.bootstrap;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * N19: ordinary tenant identity cannot read platform service status or search
 * audit events, and a platform audit search for tenant A never returns tenant B
 * or the {@code details} column.
 *
 * <p>Removing {@code requirePlatformContext} makes the tenant calls return 200
 * and fails {@link #tenantIdentityIsForbiddenOnServiceStatusAndAuditSearch()}.
 * Dropping the {@code tenant_id} predicate or selecting {@code details} fails
 * {@link #auditSearchForTenantAExcludesTenantBAndDetails()}.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class N19PlatformTenantNegativeIT {
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";
    private static final String SECRET = "n19-secret-details-must-not-leak";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
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
        registry.add("nexus.p1.demo-context.enabled", () -> "true");
        registry.add("nexus.iam.idp", () -> "stub");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.permission.persistence", () -> "memory");
        registry.add("nexus.permission.seed-tenant-member-grants", () -> "false");
        registry.add("nexus.audit.persistence", () -> "jdbc");
    }

    @Test
    void tenantIdentityIsForbiddenOnServiceStatusAndAuditSearch() throws Exception {
        UUID tenant = UUID.randomUUID();
        mvc.perform(get("/api/v1/p1/platform/services").headers(tenantOnly(tenant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P1.CONTEXT.0001"));
        mvc.perform(get("/api/v1/audit/events").headers(tenantOnly(tenant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P1.CONTEXT.0001"));
    }

    @Test
    void auditSearchForTenantAExcludesTenantBAndDetails() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        insert(eventA, tenantA, "n19-resource-a");
        insert(eventB, tenantB, "n19-resource-b");

        mvc.perform(get("/api/v1/audit/events")
                        .param("tenantId", tenantA.toString())
                        .headers(platformAudit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].event_id").value(eventA.toString()))
                .andExpect(jsonPath("$.items[0].tenant_id").value(tenantA.toString()))
                .andExpect(jsonPath("$.items[0].details").doesNotExist())
                .andExpect(content().string(not(containsString(eventB.toString()))))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString("\"details\""))));
    }

    private void insert(UUID eventId, UUID tenantId, String resourceId) {
        jdbc.update("""
                INSERT INTO platform.audit_event (
                  event_id, event_type, event_version, occurred_at, tenant_id,
                  actor_subject_id, actor_tenant_id, platform_context, reason,
                  trace_id, correlation_id, causation_id, outcome, resource_type, resource_id, details)
                VALUES (?, 'n19.audit.search', 1, now(), ?, ?::uuid, ?, false, 'n19',
                  ?, null, null, 'ACCEPTED', 'tenant', ?, '{"marker":"n19-secret-details-must-not-leak"}'::jsonb)
                """,
                eventId,
                tenantId,
                ACTOR,
                tenantId,
                "n19-" + eventId,
                resourceId);
    }

    private static HttpHeaders tenantOnly(UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR);
        headers.add(DemoPlatformContextFilter.TENANT_HEADER, tenantId.toString());
        return headers;
    }

    private static HttpHeaders platformAudit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR);
        headers.add(DemoPlatformContextFilter.REASON_HEADER, "n19-audit-search");
        return headers;
    }
}
