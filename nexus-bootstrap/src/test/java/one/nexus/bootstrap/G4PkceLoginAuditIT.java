package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * G4 leftover: Authorization Code + PKCE against native Keycloak 26.x, then
 * {@code LOGIN} on the existing JDBC hash chain by {@code X-Trace-Id}.
 * HTTP form driver, not a Chromium UI. Does not change AuditPort same-TX rules.
 * Does not reopen G18. Does not claim full G4 / G17.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
@ExtendWith(G4LiveKeycloakCondition.class)
class G4PkceLoginAuditIT {
    static final String TRACE = "0199a1c0-0000-7000-8000-00000000c216";
    private static final String REDIRECT = "http://127.0.0.1:18088/api/v1/auth/callback";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired AuditPort audits;
    @Autowired JdbcTemplate jdbc;

    private final AuthorizationCodeDriver browser = new AuthorizationCodeDriver();
    private String username;
    private String password;

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
        registry.add("nexus.iam.idp", () -> "keycloak");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.iam.smtp.enabled", () -> "false");
        registry.add("nexus.permission.persistence", () -> "memory");
        registry.add("nexus.permission.seed-tenant-member-grants", () -> "false");
        registry.add("nexus.audit.persistence", () -> "jdbc");
        registry.add("nexus.iam.keycloak.issuer", G4LiveKeycloakCondition::issuer);
        registry.add("nexus.iam.keycloak.client-id",
                () -> G4LiveKeycloakCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform"));
        registry.add("nexus.iam.keycloak.client-secret",
                () -> G4LiveKeycloakCondition.env("NEXUS_KEYCLOAK_CLIENT_SECRET", ""));
        registry.add("nexus.iam.keycloak.redirect-uri", () -> REDIRECT);
    }

    @BeforeEach
    void smokeUser() {
        username = G4LiveKeycloakCondition.env("NEXUS_KEYCLOAK_SMOKE_USERNAME", "carol");
        password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_SMOKE_PASSWORD is required for the live login form");
    }

    @Test
    void pkceAuthorizationCodeLandsLoginOnJdbcHashChain() throws Exception {
        MvcResult authorize = mvc.perform(get("/api/v1/auth/authorize-url")
                        .param("pkce", "s256")
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.code_challenge").exists())
                .andExpect(jsonPath("$.code_verifier").exists())
                .andExpect(jsonPath("$.code_challenge_method").value("S256"))
                .andReturn();
        String body = authorize.getResponse().getContentAsString();
        String authorizeUrl = extract(body, "url");
        String verifier = extract(body, "code_verifier");
        assertTrue(authorizeUrl.contains("/protocol/openid-connect/auth"), authorizeUrl);
        assertTrue(authorizeUrl.contains("response_type=code"), authorizeUrl);
        assertTrue(authorizeUrl.contains("code_challenge="), authorizeUrl);
        assertTrue(authorizeUrl.contains("code_challenge_method=S256"), authorizeUrl);

        AuthorizationCodeDriver.CodeAndState result = browser.completeLogin(
                authorizeUrl, username, password, REDIRECT);

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + result.code()
                                + "\",\"redirect_uri\":\"" + REDIRECT
                                + "\",\"code_verifier\":\"" + verifier + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").exists());

        List<ChainedAuditEvent> found = audits.findByTraceId(TRACE);
        assertTrue(found.stream().anyMatch(event ->
                        AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())
                                && TRACE.equals(event.event().traceId())),
                () -> "missing LOGIN on " + TRACE + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                TRACE,
                AuditCaptures.LOGIN_ACCEPTED);
        assertTrue(rows != null && rows >= 1, "jdbc LOGIN rows for " + TRACE);
        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    private static String extract(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            throw new IllegalStateException("missing " + field + " in " + json);
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
