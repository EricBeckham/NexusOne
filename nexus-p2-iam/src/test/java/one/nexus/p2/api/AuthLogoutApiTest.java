package one.nexus.p2.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"nexus.iam.persistence=memory", "nexus.iam.idp=stub"})
@AutoConfigureMockMvc
class AuthLogoutApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    one.nexus.p2.IdentityModule module;

    @BeforeEach
    void createActiveMembershipInMemory() {
        String suffix = java.util.UUID.randomUUID().toString();
        module.adminInitializer().initialize(new one.nexus.p2.admin.TenantAdminInitializer.Command(
                java.util.UUID.randomUUID(), "Logout API test", suffix + "@example.invalid", suffix));
    }

    @Test
    void logoutWithCurrentContextHeaderLeavesTheOtherBrowserContextUsable() throws Exception {
        String a = loginResponse();
        String b = loginResponse();
        String token = one.nexus.p2.idp.StubIdentityProvider.field(a, "accessToken");
        String contextA = one.nexus.p2.idp.StubIdentityProvider.field(a, "contextId");
        String contextB = one.nexus.p2.idp.StubIdentityProvider.field(b, "contextId");
        org.junit.jupiter.api.Assertions.assertNotEquals(contextA, contextB);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)
                        .header(IdentityAuthFilter.HEADER_CONTEXT, contextA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token)
                        .header(IdentityAuthFilter.HEADER_CONTEXT, contextA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("tenant context is revoked"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token)
                        .header(IdentityAuthFilter.HEADER_CONTEXT, contextB))
                .andExpect(status().isOk());
    }

    private String loginResponse() throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void logoutAnswersWithAnEndSessionUrlFieldEvenWhenTheIdpHasNone() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = one.nexus.p2.idp.StubIdentityProvider.field(login.getResponse().getContentAsString(), "accessToken");

        // The stub IdP has no end_session endpoint: the app session is still cleared, the URL is empty.
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_url").value(""));
    }

    @Test
    void postLogoutRedirectIsTheConfiguredOriginPlusLoginOnly() {
        assertEquals("https://nexus.example.test/login",
                AuthController.postLogoutRedirectUri("https://nexus.example.test/callback"));
        assertEquals("http://127.0.0.1:5173/login",
                AuthController.postLogoutRedirectUri("http://127.0.0.1:5173/callback?x=1"));
        assertEquals("https://nexus.example.test/login",
                AuthController.postLogoutRedirectUri("HTTPS://nexus.example.test/deep/path"));
        assertNull(AuthController.postLogoutRedirectUri(null));
        assertNull(AuthController.postLogoutRedirectUri(" "));
        assertNull(AuthController.postLogoutRedirectUri("javascript:alert(1)"));
        assertNull(AuthController.postLogoutRedirectUri("/callback"));
        assertNull(AuthController.postLogoutRedirectUri("http://bad host/callback"));
    }
}
