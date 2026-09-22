package one.nexus.p2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * G18 leftover guard: default stub admin token still issues a context.
 * Fabricated {@code acr=mfa} is not a TOTP challenge.
 */
@SpringBootTest
@AutoConfigureMockMvc
class G18StubAdminLoginTest {
    private static final UUID TENANT = UUID.fromString("01800000-0000-7000-8000-000000000018");

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @Test
    void stubAdminTokenStillIssuesContextForClosedG18Walk() throws Exception {
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G18 Stub Admin", "g18-stub-admin@example.com", "g18-stub-admin"));
        String token = StubIdentityProvider.token(
                "g18-stub-admin", "g18-stub-admin@example.com", "g18-stub-admin", "G18 Stub Admin");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.contextId").exists());
    }
}
