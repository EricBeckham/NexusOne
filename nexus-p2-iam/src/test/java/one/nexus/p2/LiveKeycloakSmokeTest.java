package one.nexus.p2;

import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live Keycloak 26.5.2 smoke. Skips when OIDC discovery is unreachable.
 * Stub IdP is off ({@code nexus.iam.idp=keycloak}). Direct-grant is not used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@ExtendWith(KeycloakAvailableCondition.class)
class LiveKeycloakSmokeTest {
    private static final String REDIRECT = "http://127.0.0.1:18088/api/v1/auth/callback";
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @DynamicPropertySource
    static void liveKeycloak(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> "18088");
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
    IdentityModule module;

    @Autowired
    IdentityProviderPort idp;

    private final AuthorizationCodeDriver browser = new AuthorizationCodeDriver();
    private String username;
    private String password;
    private String email;

    @BeforeEach
    void smokeUser() {
        username = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_SMOKE_USERNAME", "carol");
        password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        email = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_SMOKE_EMAIL", "carol@example.com");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_SMOKE_PASSWORD is required for the live login form");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                idp.getClass().getName().contains("Keycloak"),
                "Stub IdP must be off for live smoke");
    }

    @Test
    void authorizationCodeIssuesContextAndRejectsClientTenantHeader() throws Exception {
        recordKeycloakVersion();

        mvc.perform(post("/api/v1/internal/tenants/" + TENANT_A + "/admin-users")
                        .header("X-Nexus-Bootstrap-Token", "test-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Admin A\",\"email\":\"live-admin-a@example.com\",\"username\":\"live-admin-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_B, "Admin B", "live-admin-b@example.com", "live-admin-b"));

        MvcResult authorize = mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andReturn();
        String authorizeUrl = field(authorize.getResponse().getContentAsString(), "url");
        assertTrue(authorizeUrl.contains("/protocol/openid-connect/auth"), authorizeUrl);
        assertTrue(authorizeUrl.contains("response_type=code"), authorizeUrl);
        assertFalse(authorizeUrl.contains("code_challenge"), "browser PKCE is not the product path");

        AuthorizationCodeDriver.CodeAndState first = browser.completeLogin(
                authorizeUrl, username, password, REDIRECT);

        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + first.code() + "\",\"redirectUri\":\"" + REDIRECT + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String loginBody = loggedIn.getResponse().getContentAsString();
        assertTrue(loginBody.contains("accountId"), loginBody);

        var carol = module.repository().findAccountByEmail(email)
                .or(() -> module.repository().findAccountByEmail(username + "@example.com"))
                .orElseGet(() -> module.repository().findAccountByEmail("carol@example.com").orElseThrow());
        var adminA = module.repository().findAccountByEmail("live-admin-a@example.com").orElseThrow();
        var personA = module.repository().findPersonByAccount(TENANT_A, adminA.id()).orElseThrow();
        bind(TENANT_A, adminA.id(), personA.id());
        module.memberships().add(carol.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Carol A", "C-A", email, null, null, carol.id()));
        TenantContextHolder.clear();

        var adminB = module.repository().findAccountByEmail("live-admin-b@example.com").orElseThrow();
        var personB = module.repository().findPersonByAccount(TENANT_B, adminB.id()).orElseThrow();
        bind(TENANT_B, adminB.id(), personB.id());
        module.memberships().add(carol.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Carol B", "C-B", email, null, null, carol.id()));
        TenantContextHolder.clear();

        MvcResult authorizeAgain = mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andReturn();
        AuthorizationCodeDriver.CodeAndState second = browser.completeLogin(
                field(authorizeAgain.getResponse().getContentAsString(), "url"),
                username, password, REDIRECT);
        var tokens = idp.exchangeAuthorizationCode(second.code(), REDIRECT);
        assertTrue(tokens.accessToken() != null && !tokens.accessToken().isBlank());
        assertTrue(idp.validate(tokens.accessToken()).subject() != null);

        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P2.IAM.CLIENT_TENANT_REJECTED"));

        MvcResult switched = mvc.perform(post("/api/v1/auth/tenant-switch")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT_A + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
                .andExpect(jsonPath("$.contextId").value(notNullValue()))
                .andReturn();
        String contextId = field(switched.getResponse().getContentAsString(), "contextId");

        mvc.perform(get("/api/v1/persons")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName=='Carol A')]").exists())
                .andExpect(jsonPath("$[?(@.displayName=='Carol B')]").doesNotExist());

        MvcResult switchedB = mvc.perform(post("/api/v1/auth/tenant-switch")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT_B + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_B.toString()))
                .andReturn();
        String contextB = field(switchedB.getResponse().getContentAsString(), "contextId");
        mvc.perform(get("/api/v1/persons")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Nexus-Context-Id", contextB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName=='Carol B')]").exists())
                .andExpect(jsonPath("$[?(@.displayName=='Carol A')]").doesNotExist());

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_url").exists());
    }

    private static void recordKeycloakVersion() throws Exception {
        String base = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_BASE", "http://127.0.0.1:8180");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> discovery = http.send(
                HttpRequest.newBuilder(URI.create(base + "/realms/nexus/.well-known/openid-configuration"))
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(discovery.statusCode() == 200, "OIDC discovery failed: " + discovery.statusCode());
        HttpResponse<String> realm = http.send(
                HttpRequest.newBuilder(URI.create(base + "/realms/nexus"))
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(realm.statusCode() == 200 && realm.body().contains("\"realm\":\"nexus\""),
                "realm descriptor failed: " + realm.statusCode());
        System.out.println("LIVE_KEYCLOAK_ISSUER_DISCOVERY_OK issuer present="
                + discovery.body().contains("\"issuer\"")
                + " realm=nexus client=nexus-platform");
    }

    private static void bind(UUID tenantId, UUID accountId, UUID personId) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, accountId, personId, "tmp",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)));
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
