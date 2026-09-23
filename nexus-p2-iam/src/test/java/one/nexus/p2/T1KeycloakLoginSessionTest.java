package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.account.AccountPersonMaster;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T1-L2 acceptance (stub IdP): own-user provisioned via {@link AccountPersonMaster}
 * completes authorize-url → auth-code login (browser password form analogue) and
 * keeps the issued tenant session on subsequent requests.
 * Non-goals: external IdP, SMTP, product shells (L3).
 */
@SpringBootTest
@AutoConfigureMockMvc
class T1KeycloakLoginSessionTest {
    private static final UUID TENANT = UUID.fromString("018f0000-0000-7000-8000-00000000b222");

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void ownUserAuthCodeLoginIssuesAndHoldsSession() throws Exception {
        AccountPersonMaster.Result created = module.accountPersonMaster().ensure(new AccountPersonMaster.Command(
                TENANT,
                "T1 L2 User",
                "t1-l2-user@example.com",
                "t1-l2-user",
                "E-T1L2",
                "t1-l2-password"));
        assertNotNull(created.idpSubject());
        assertEquals(TENANT, created.tenantId());

        MvcResult authorize = mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.state").exists())
                .andReturn();
        String authorizeBody = authorize.getResponse().getContentAsString();
        String authorizeUrl = StubIdentityProvider.field(authorizeBody, "url");
        org.junit.jupiter.api.Assertions.assertTrue(
                authorizeUrl.contains("code=stub-code"),
                "stub authorize-url stands in for Keycloak browser password form: " + authorizeUrl);

        MvcResult loggedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(created.accountId().toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.personId").value(created.personId().toString()))
                .andExpect(jsonPath("$.contextId").exists())
                .andReturn();

        String loginBody = loggedIn.getResponse().getContentAsString();
        String contextId = StubIdentityProvider.field(loginBody, "contextId");
        String accessToken = StubIdentityProvider.tokenWithoutMfa(
                created.idpSubject(),
                "t1-l2-user@example.com",
                "t1-l2-user",
                "T1 L2 User");

        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextId").value(contextId))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.accountId").value(created.accountId().toString()))
                .andExpect(jsonPath("$.personId").value(created.personId().toString()));

        // Session still held on a second authenticated call with the same context.
        mvc.perform(get("/api/v1/persons")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + created.personId() + "')]").exists());
    }

    @Test
    void bootstrapApiCanSeedPasswordForOwnUserLogin() throws Exception {
        UUID tenant = UUID.fromString("018f0000-0000-7000-8000-00000000b223");

        mvc.perform(post("/api/v1/internal/tenants/" + tenant + "/loginable-persons")
                        .header("X-Nexus-Bootstrap-Token", "test-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"API L2\",\"email\":\"t1-l2-api@example.com\","
                                + "\"username\":\"t1-l2-api\",\"employeeNo\":\"E-L2API\",\"password\":\"api-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idpSubject").exists())
                .andExpect(jsonPath("$.createdAccount").value(true));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$.contextId").exists());
    }
}
