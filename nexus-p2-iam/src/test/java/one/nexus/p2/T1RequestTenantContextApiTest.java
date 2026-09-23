package one.nexus.p2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** T1-L0 acceptance: login requests receive a server-issued tenant context and cannot cross tenants. */
@SpringBootTest
@AutoConfigureMockMvc
class T1RequestTenantContextApiTest {
    private static final UUID TENANT_ALPHA = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private static final UUID TENANT_BETA = UUID.fromString("01800000-0000-7000-8000-000000000102");

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @Test
    void loginIssuesTenantContextAndRejectsCrossTenantReuse() throws Exception {
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_ALPHA, "T1 Alpha Admin", "t1-alpha@example.com", "t1-alpha"));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_BETA, "T1 Beta Admin", "t1-beta@example.com", "t1-beta"));

        String alphaToken = StubIdentityProvider.token(
                "t1-alpha", "t1-alpha@example.com", "t1-alpha", "T1 Alpha Admin");
        String betaToken = StubIdentityProvider.token(
                "t1-beta", "t1-beta@example.com", "t1-beta", "T1 Beta Admin");

        MvcResult alphaLogin = login(alphaToken, TENANT_ALPHA);
        String alphaContext = StubIdentityProvider.field(
                alphaLogin.getResponse().getContentAsString(), "contextId");

        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + alphaToken)
                        .header("X-Nexus-Context-Id", alphaContext))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ALPHA.toString()))
                .andExpect(jsonPath("$.contextId").value(alphaContext));

        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + betaToken)
                        .header("X-Nexus-Context-Id", alphaContext))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.IAM.FORBIDDEN"));

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant-Id", TENANT_ALPHA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + betaToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P2.IAM.CLIENT_TENANT_REJECTED"));

        login(betaToken, TENANT_BETA);
        org.junit.jupiter.api.Assertions.assertNull(
                TenantContextHolder.get(), "request completion must clear the tenant context");
    }

    private MvcResult login(String token, UUID expectedTenant) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(expectedTenant.toString()))
                .andExpect(jsonPath("$.contextId").isNotEmpty())
                .andReturn();
    }
}
