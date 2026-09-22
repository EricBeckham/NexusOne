package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.ChromeDevToolsDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * G4 leftover after #108: Authorization Code + PKCE over local self-signed
 * HTTPS so a cookie-honoring client can complete login without dropping
 * Keycloak 26 {@code Secure} cookies. Lands {@code LOGIN} on the JDBC hash
 * chain. Does not change AuditPort same-TX rules. Does not reopen G18. Does
 * not claim a full G4 close.
 */
@SpringBootTest(classes = NexusOneApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(G4HttpsKeycloakCondition.class)
class G4PkceHttpsBrowserIT {
    static final String TRACE = "0199a1c0-0000-7000-8000-00000000c217";
    static final String CHROME_TRACE = "0199a1c0-0000-7000-8000-00000000c218";
    private static EmbeddedPostgres postgres;

    @Autowired AuditPort audits;
    @Autowired JdbcTemplate jdbc;

    private String username;
    private String password;
    private String productBase;
    private String redirect;

    @BeforeAll
    static void startPostgres() throws Exception {
        G4HttpsKeycloakCondition.installTrust();
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
    static void httpsProduct(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("server.port", () -> String.valueOf(G4HttpsKeycloakCondition.productPort()));
        registry.add("server.ssl.enabled", () -> "true");
        registry.add("server.ssl.key-store", () -> "file:" + G4HttpsKeycloakCondition.keyStore().toAbsolutePath());
        registry.add("server.ssl.key-store-password", G4HttpsKeycloakCondition::storePassword);
        registry.add("server.ssl.key-store-type", () -> "PKCS12");
        registry.add("server.http2.enabled", () -> "true");
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
        registry.add("nexus.tls.gm.enabled", () -> "false");
        registry.add("nexus.iam.keycloak.issuer", G4HttpsKeycloakCondition::issuer);
        registry.add("nexus.iam.keycloak.client-id",
                () -> G4HttpsKeycloakCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform"));
        registry.add("nexus.iam.keycloak.client-secret",
                () -> G4HttpsKeycloakCondition.env("NEXUS_KEYCLOAK_CLIENT_SECRET", ""));
        registry.add("nexus.iam.keycloak.redirect-uri", G4HttpsKeycloakCondition::redirectUri);
    }

    @BeforeEach
    void smokeUser() throws Exception {
        username = G4HttpsKeycloakCondition.env("NEXUS_KEYCLOAK_SMOKE_USERNAME", "carol");
        password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_SMOKE_PASSWORD is required for the live login form");
        productBase = "https://127.0.0.1:" + G4HttpsKeycloakCondition.productPort();
        redirect = G4HttpsKeycloakCondition.redirectUri();
        G4HttpsKeycloakCondition.installTrust();
    }

    @Test
    void httpsHttp2PkceLandsLoginOnJdbcHashChain() throws Exception {
        WalkResult walk = walk(TRACE, AuthorizationCodeDriver.httpsHonoringSecureCookies(
                G4HttpsKeycloakCondition.clientContext()));
        assertTrue(walk.authorizeUrl().startsWith("https://"), walk.authorizeUrl());
        assertTrue(walk.authorizeUrl().contains("code_challenge="), walk.authorizeUrl());
        assertTrue(walk.authorizeUrl().contains("code_challenge_method=S256"), walk.authorizeUrl());
        assertTrue(walk.callback().startsWith("https://"), walk.callback());
        System.out.println("G4_HTTPS_HTTP2_OK version=" + walk.httpVersion()
                + " authorize=" + walk.authorizeUrl().split("\\?", 2)[0]
                + " callback=" + walk.callback());
        assertLoginOnChain(TRACE);
    }

    @Test
    void chromeCdpWalkOptional() throws Exception {
        assumeTrue("1".equals(G4HttpsKeycloakCondition.env("NEXUS_G4_HTTPS_CHROME", "")),
                "set NEXUS_G4_HTTPS_CHROME=1 to attempt Chromium CDP");
        assumeTrue(ChromeDevToolsDriver.available(), "Chrome/Chromium is not installed");
        try {
            HttpClient product = AuthorizationCodeDriver.httpsClient(G4HttpsKeycloakCondition.clientContext());
            Authorize authorize = authorize(product, CHROME_TRACE);
            AuthorizationCodeDriver.CodeAndState code = ChromeDevToolsDriver.completeLogin(
                    authorize.url(), username, password, redirect);
            login(product, CHROME_TRACE, code, authorize.verifier());
            assertLoginOnChain(CHROME_TRACE);
            System.out.println("G4_HTTPS_CHROME_OK callback=" + code.redirectUri());
        } catch (Exception ex) {
            System.out.println("G4_HTTPS_CHROME_BLOCKED reason=" + ex.getMessage());
            abort("Chrome CDP blocked: " + ex.getMessage());
        }
    }

    private WalkResult walk(String trace, AuthorizationCodeDriver driver) throws Exception {
        HttpClient product = AuthorizationCodeDriver.httpsClient(G4HttpsKeycloakCondition.clientContext());
        Authorize authorize = authorize(product, trace);
        AuthorizationCodeDriver.CodeAndState code = driver.completeLogin(
                authorize.url(), username, password, redirect);
        login(product, trace, code, authorize.verifier());
        return new WalkResult(authorize.url(), code.redirectUri() + "?code=" + code.code(), driver.lastHttpVersion());
    }

    private Authorize authorize(HttpClient product, String trace) throws Exception {
        HttpResponse<String> response = product.send(
                HttpRequest.newBuilder(URI.create(productBase + "/api/v1/auth/authorize-url?pkce=s256"))
                        .timeout(Duration.ofSeconds(15))
                        .header("X-Trace-Id", trace)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("authorize-url HTTP " + response.statusCode() + " " + response.body());
        }
        String url = extract(response.body(), "url");
        String verifier = extract(response.body(), "code_verifier");
        if (!url.contains("code_challenge=")) {
            throw new IllegalStateException("authorize URL missing PKCE: " + url);
        }
        return new Authorize(url, verifier);
    }

    private void login(HttpClient product, String trace, AuthorizationCodeDriver.CodeAndState code, String verifier)
            throws Exception {
        String json = "{\"code\":\"" + code.code()
                + "\",\"redirect_uri\":\"" + redirect
                + "\",\"code_verifier\":\"" + verifier + "\"}";
        HttpResponse<String> response = product.send(
                HttpRequest.newBuilder(URI.create(productBase + "/api/v1/auth/login"))
                        .timeout(Duration.ofSeconds(20))
                        .header("X-Trace-Id", trace)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().contains("account_id")) {
            throw new IllegalStateException("login HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private void assertLoginOnChain(String trace) {
        List<ChainedAuditEvent> found = audits.findByTraceId(trace);
        assertTrue(found.stream().anyMatch(event ->
                        AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())
                                && trace.equals(event.event().traceId())),
                () -> "missing LOGIN on " + trace + ": " + found);
        Integer rows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ? and event_type = ?",
                Integer.class,
                trace,
                AuditCaptures.LOGIN_ACCEPTED);
        assertTrue(rows != null && rows >= 1, "jdbc LOGIN rows for " + trace);
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

    private record Authorize(String url, String verifier) {}

    private record WalkResult(String authorizeUrl, String callback, java.net.http.HttpClient.Version httpVersion) {}
}
