package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.Totp;
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
 * Live Keycloak 26.5.2 admin TOTP leftover. Opt-in ({@code NEXUS_KEYCLOAK_SMOKE=1}).
 * Forces a {@code TENANT_ADMIN} through CONFIGURE_TOTP once, proves
 * {@code P2.IAM.MFA_REQUIRED} without OTP, then issues a context after OTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(KeycloakAvailableCondition.class)
class LiveKeycloakAdminTotpTest {
    private static final String REDIRECT = "http://127.0.0.1:18088/api/v1/auth/callback";
    private static final UUID TENANT = UUID.fromString("01700000-0000-7000-8000-000000000017");

    @DynamicPropertySource
    static void liveKeycloak(DynamicPropertyRegistry registry) {
        registry.add("nexus.iam.idp", () -> "keycloak");
        registry.add("nexus.iam.persistence", () -> "memory");
        registry.add("nexus.iam.smtp.enabled", () -> "false");
        registry.add("nexus.iam.mfa.admin-required", () -> "true");
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
    private KeycloakAdminSupport admin;
    private String username;
    private String password;
    private String email;

    @BeforeEach
    void adminUser() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                idp.getClass().getName().contains("Keycloak"),
                "Stub IdP must be off for live admin TOTP");
        password = System.getenv("NEXUS_KEYCLOAK_ADMIN_TOTP_PASSWORD");
        if (password == null || password.isBlank()) {
            password = System.getenv("NEXUS_KEYCLOAK_SMOKE_PASSWORD");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "NEXUS_KEYCLOAK_ADMIN_TOTP_PASSWORD or NEXUS_KEYCLOAK_SMOKE_PASSWORD is required");
        String issuer = KeycloakAvailableCondition.env(
                "NEXUS_KEYCLOAK_ISSUER", "http://127.0.0.1:8180/realms/nexus");
        String clientId = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform");
        String secret = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_SECRET", "");
        admin = new KeycloakAdminSupport(issuer, clientId, secret);
        username = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_ADMIN_TOTP_USERNAME", "tenant-admin-totp");
        email = KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_ADMIN_TOTP_EMAIL", username + "@example.com");
    }

    @Test
    void tenantAdminPasswordOnlyRejectedThenTotpIssuesContext() throws Exception {
        String clientUuid = admin.findClientUuid(
                KeycloakAvailableCondition.env("NEXUS_KEYCLOAK_CLIENT_ID", "nexus-platform"));
        assertNotNull(clientUuid, "nexus-platform client");
        admin.ensureAmrMapper(clientUuid);
        List<String> refs = admin.ensureAuthenticatorReferences();
        assertFalse(refs.isEmpty(), "browser OTP/password AMR references");
        System.out.println("G17_ADMIN_TOTP_AMR_REFS " + refs);

        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Tenant Admin TOTP", email, username));
        String userId = admin.findUserId(username);
        assertNotNull(userId, "Keycloak user " + username);
        admin.setPassword(userId, password);
        admin.deleteOtpCredentials(userId);
        admin.setRequiredActions(userId, List.of());
        admin.assignRealmRole(userId, "TENANT_ADMIN");

        String authorizeUrl = authorizeUrl(null);
        assertFalse(authorizeUrl.contains("code_challenge"), "product path stays confidential-client");
        AuthorizationCodeDriver.CodeAndState passwordOnly = browser.completeLogin(
                authorizeUrl, username, password, REDIRECT);
        IdentityProviderPort.TokenSet passwordTokens = idp.exchangeAuthorizationCode(
                passwordOnly.code(), REDIRECT);
        IdentityProviderPort.IdentityClaims passwordClaims = idp.validate(passwordTokens.accessToken());
        assertFalse(passwordClaims.mfaSatisfied(),
                "password-only token must not satisfy MFA acr=" + passwordClaims.acr()
                        + " amr=" + passwordClaims.amr());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + passwordTokens.accessToken() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.IAM.MFA_REQUIRED"))
                .andExpect(jsonPath("$.contextId").doesNotExist());
        System.out.println("G17_ADMIN_TOTP_PASSWORD_ONLY P2.IAM.MFA_REQUIRED acr="
                + passwordClaims.acr() + " amr=" + passwordClaims.amr());

        admin.setRequiredActions(userId, List.of("CONFIGURE_TOTP"));
        AuthorizationCodeDriver.TotpEnrollment enrolled = browser.enrollTotp(
                authorizeUrl(null), username, password, REDIRECT);
        assertNotNull(enrolled.secret());
        assertTrue(admin.userHasOtp(userId), "Keycloak stored an otp credential after enrollment");
        System.out.println("G17_ADMIN_TOTP_ENROLLED secret_len=" + enrolled.secret().length());

        IdentityProviderPort.TokenSet enrolledTokens = idp.exchangeAuthorizationCode(
                enrolled.callback().code(), REDIRECT);
        IdentityProviderPort.IdentityClaims enrolledClaims = idp.validate(enrolledTokens.accessToken());
        System.out.println("G17_ADMIN_TOTP_ENROLL_TOKEN acr=" + enrolledClaims.acr()
                + " amr=" + enrolledClaims.amr() + " mfa=" + enrolledClaims.mfaSatisfied());

        AuthorizationCodeDriver.ChallengePage otpPage = browser.submitPassword(
                browser.open(authorizeUrl(null), REDIRECT), username, password);
        assertTrue(otpPage.kind() == AuthorizationCodeDriver.FormKind.OTP, otpPage.html());
        System.out.println("G17_ADMIN_TOTP_CHALLENGE kind=" + otpPage.kind());

        String otp = Totp.generate(enrolled.secret());
        AuthorizationCodeDriver.ChallengePage afterOtp = browser.submitOtp(otpPage, otp);
        assertTrue(afterOtp.isRedirect(), "OTP must complete authorization code, kind=" + afterOtp.kind());
        IdentityProviderPort.TokenSet otpTokens = idp.exchangeAuthorizationCode(
                afterOtp.redirect().code(), REDIRECT);
        IdentityProviderPort.IdentityClaims otpClaims = idp.validate(otpTokens.accessToken());
        assertTrue(otpClaims.mfaSatisfied(),
                "OTP token must satisfy MFA acr=" + otpClaims.acr() + " amr=" + otpClaims.amr());
        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + otpTokens.accessToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.contextId").exists())
                .andReturn();
        String contextId = field(loggedIn.getResponse().getContentAsString(), "contextId");
        assertNotNull(contextId);
        assertFalse(contextId.isBlank());
        System.out.println("G17_ADMIN_TOTP_OTP_OK acr=" + otpClaims.acr() + " amr=" + otpClaims.amr()
                + " context=" + contextId);
    }

    private String authorizeUrl(String acr) throws Exception {
        var request = get("/api/v1/auth/authorize-url");
        if (acr != null) {
            request = request.param("acr", acr);
        }
        MvcResult authorize = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return field(authorize.getResponse().getContentAsString(), "url");
    }

    private static String field(String json, String name) {
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalStateException("missing " + name + " in " + json);
        }
        int from = start + key.length();
        return json.substring(from, json.indexOf('"', from));
    }
}
