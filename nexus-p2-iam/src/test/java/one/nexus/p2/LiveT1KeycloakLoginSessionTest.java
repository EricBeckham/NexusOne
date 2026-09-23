package one.nexus.p2;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.account.AccountPersonMaster;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
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

/**
 * T1-L2 live acceptance: AccountPersonMaster own-user + Keycloak browser
 * username/password → session issued and held. Opt-in via
 * {@code NEXUS_KEYCLOAK_SMOKE=1} (same gate as other live Keycloak tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(KeycloakAvailableCondition.class)
class LiveT1KeycloakLoginSessionTest {
    private static final String REDIRECT = "http://127.0.0.1:18088/api/v1/auth/callback";
    private static final UUID TENANT = UUID.fromString("018f0000-0000-7000-8000-00000000b224");

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
    IdentityModule module;

    @Autowired
    IdentityProviderPort idp;

    private final AuthorizationCodeDriver browser = new AuthorizationCodeDriver();
    private String username;
    private String password;
    private String email;

    @BeforeEach
    void ownUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                idp.getClass().getName().contains("Keycloak"),
                "Stub IdP must be off for live T1-L2");
        password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_SMOKE_PASSWORD is required");
        String suffix = Long.toString(System.currentTimeMillis() % 1_000_000);
        username = "t1l2-" + suffix;
        email = username + "@example.com";
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void ownUserBrowserPasswordLoginIssuesAndHoldsSession() throws Exception {
        AccountPersonMaster.Result created = module.accountPersonMaster().ensure(new AccountPersonMaster.Command(
                TENANT, "T1 L2 Live", email, username, "E-T1L2-LIVE", password));
        assertTrue(created.idpSubject() != null && !created.idpSubject().isBlank());

        MvcResult authorize = mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andReturn();
        String authorizeUrl = field(authorize.getResponse().getContentAsString(), "url");
        assertTrue(authorizeUrl.contains("/protocol/openid-connect/auth"), authorizeUrl);

        AuthorizationCodeDriver.CodeAndState code = browser.completeLogin(
                authorizeUrl, username, password, REDIRECT);

        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.code() + "\",\"redirectUri\":\"" + REDIRECT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(created.accountId().toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.personId").value(created.personId().toString()))
                .andExpect(jsonPath("$.contextId").value(notNullValue()))
                .andReturn();
        String contextId = field(loggedIn.getResponse().getContentAsString(), "contextId");

        // Fresh browser login yields a new access token; issued context must still hold.
        MvcResult authorizeAgain = mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andReturn();
        AuthorizationCodeDriver.CodeAndState again = browser.completeLogin(
                field(authorizeAgain.getResponse().getContentAsString(), "url"),
                username,
                password,
                REDIRECT);
        var heldTokens = idp.exchangeAuthorizationCode(again.code(), REDIRECT);

        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + heldTokens.accessToken())
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextId").value(contextId))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.personId").value(created.personId().toString()));

        mvc.perform(get("/api/v1/persons")
                        .header("Authorization", "Bearer " + heldTokens.accessToken())
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + created.personId() + "')]").exists());

        System.out.println("T1_L2_LIVE_OK subject=" + created.idpSubject() + " context=" + contextId);
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
