package one.nexus.p2;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMfaApiTest {
    private static final UUID TENANT = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @Test
    void adminPasswordOnlyTokenIsRejectedAndMfaTokenIssuesContext() throws Exception {
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Http Admin", "http-admin@example.com", "http-admin"));

        String passwordOnly = StubIdentityProvider.tokenWithoutMfa(
                "http-admin", "http-admin@example.com", "http-admin", "Http Admin");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + passwordOnly + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.IAM.MFA_REQUIRED"))
                .andExpect(jsonPath("$.contextId").doesNotExist());

        String mfa = StubIdentityProvider.token(
                "http-admin", "http-admin@example.com", "http-admin", "Http Admin");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + mfa + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.contextId").exists())
                .andExpect(jsonPath("$.contextId").value(org.hamcrest.Matchers.not(nullValue())));

        mvc.perform(get("/api/v1/auth/authorize-url").param("acr", "mfa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("acr_values=mfa")));
    }
}
