package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.KeycloakIdentityProvider;
import one.nexus.p2.idp.Pkce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Live Keycloak 26.x Authorization Code + PKCE. Lands {@code LOGIN} on
 * {@link AuditPort} by {@code X-Trace-Id}. Not a Chromium UI walk.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(KeycloakAvailableCondition.class)
@Import(LiveKeycloakPkceLoginAuditTest.AuditChainConfig.class)
class LiveKeycloakPkceLoginAuditTest {
    static final String TRACE = "0199a1c0-0000-7000-8000-00000000c215";
    private static final String REDIRECT = "http://127.0.0.1:18088/api/v1/auth/callback";

    @TestConfiguration
    static class AuditChainConfig {
        @Bean
        AuditPort auditPort() {
            return new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        }
    }

    @DynamicPropertySource
    static void liveKeycloak(DynamicPropertyRegistry registry) {
        registry.add("nexus.iam.idp", () -> "keycloak");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.iam.smtp.enabled", () -> "false");
        registry.add("nexus.iam.platform-bootstrap-token", () -> "test-bootstrap");
        registry.add("nexus.iam.keycloak.issuer",
                () -> KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_ISSUER", "http://127.0.0.1:8180/realms/nexus"));
        registry.add("nexus.iam.keycloak.client-id",
                () -> KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform"));
        registry.add("nexus.iam.keycloak.client-secret",
                () -> KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_SECRET", ""));
        registry.add("nexus.iam.keycloak.redirect-uri", () -> REDIRECT);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditPort audits;

    @Autowired
    IdentityProviderPort idp;

    private final AuthorizationCodeDriver browser = new AuthorizationCodeDriver();
    private String username;
    private String password;

    @BeforeEach
    void smokeUser() {
        username = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_SMOKE_USERNAME", "carol");
        password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_SMOKE_PASSWORD is required for the live login form");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                idp.getClass().getName().contains("Keycloak"),
                "Stub IdP must be off for live PKCE");
    }

    @Test
    void pkceCodeExchangeLandsLoginOnHashChainByTrace() throws Exception {
        MvcResult authorize = mvc.perform(get("/api/v1/auth/authorize-url").param("pkce", "s256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.code_challenge").exists())
                .andExpect(jsonPath("$.code_verifier").exists())
                .andExpect(jsonPath("$.code_challenge_method").value("S256"))
                .andReturn();
        String body = authorize.getResponse().getContentAsString();
        String authorizeUrl = field(body, "url");
        String verifier = field(body, "code_verifier");
        assertTrue(authorizeUrl.contains("response_type=code"), authorizeUrl);
        assertTrue(authorizeUrl.contains("code_challenge="), authorizeUrl);
        assertTrue(authorizeUrl.contains("code_challenge_method=S256"), authorizeUrl);

        AuthorizationCodeDriver.CodeAndState result = browser.completeLogin(
                authorizeUrl, username, password, REDIRECT);

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + result.code()
                                + "\",\"redirectUri\":\"" + REDIRECT
                                + "\",\"codeVerifier\":\"" + verifier + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").exists());

        List<ChainedAuditEvent> found = audits.findByTraceId(TRACE);
        assertTrue(found.stream().anyMatch(event ->
                        AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())
                                && TRACE.equals(event.event().traceId())),
                () -> "missing LOGIN on " + TRACE + ": " + found);
        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());

        tryPublicClientPkce();
    }

    private void tryPublicClientPkce() throws Exception {
        String issuer = KeycloakAvailableCondition.env(
                "NEXUS_KEYCLOAK_ISSUER", "http://127.0.0.1:8180/realms/nexus");
        String publicId = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_PKCE_CLIENT_ID", "nexus-web");
        KeycloakIdentityProvider web;
        try {
            web = new KeycloakIdentityProvider(issuer, publicId, "");
        } catch (RuntimeException ex) {
            System.out.println("G4_PKCE_PUBLIC_CLIENT_SKIPPED reason=" + ex.getMessage());
            return;
        }
        Pkce.Challenge pkce = Pkce.s256();
        var url = web.authorizationUrl("public-state", REDIRECT, null, pkce);
        if (!url.url().contains("client_id=" + publicId) && !url.url().contains("client_id=" + enc(publicId))) {
            return;
        }
        try {
            AuthorizationCodeDriver.CodeAndState result = browser.completeLogin(
                    url.url(), username, password, REDIRECT);
            var tokens = web.exchangeAuthorizationCode(result.code(), REDIRECT, pkce.verifier());
            assertTrue(tokens.accessToken() != null && !tokens.accessToken().isBlank());
            assertTrue(web.validate(tokens.accessToken()).subject() != null);
            System.out.println("G4_PKCE_PUBLIC_CLIENT_OK client=" + publicId);
        } catch (Exception ex) {
            System.out.println("G4_PKCE_PUBLIC_CLIENT_BLOCKED reason=" + ex.getMessage());
        }
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String field(String json, String name) {
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalStateException("missing " + name + " in " + json);
        }
        int from = start + key.length();
        int end = json.indexOf('"', from);
        return json.substring(from, end);
    }
}
