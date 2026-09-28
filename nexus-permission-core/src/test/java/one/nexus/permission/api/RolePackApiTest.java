package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** PLA7: C3 role-pack landing over HTTP (operations only), behind the PLA0 guard. */
@SpringBootTest
@AutoConfigureMockMvc
class RolePackApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    String[] login(String prefix, boolean admin) {
        UUID tenant = UUID.randomUUID();
        String sub = prefix + tenant.toString().substring(0, 8);
        if (admin) {
            identity.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Pack", sub + "@example.com", sub));
        }
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String context = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.clear();
        return new String[] {token, context};
    }

    static final String SNAKE = """
            {"app_code":"leave","app_version":"1.0.0","role_pack":[{"code":"leave_applicant","name":"请假申请人",
             "grants":[{"resource":"fn.leave.leave_apply","action":"read","scope":"SELF"}]}]}""";
    static final String CAMEL = """
            {"appCode":"leave","appVersion":"1.1.0","rolePack":[{"code":"leave_applicant","name":"请假申请人",
             "grants":[{"resource":"fn.leave.leave_apply","action":"read","scope":"SELF"},
                       {"resource":"fn.leave.leave_apply","action":"write","scope":"SELF"}]}]}""";

    @Test
    void theTenantAdminLandsAPackIdempotentlyWithEitherFieldSpelling() throws Exception {
        String[] admin = login("pack-admin-", true);
        mvc.perform(post("/api/v1/permission/role-packs/apply").contentType(MediaType.APPLICATION_JSON).content(SNAKE)
                        .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].tenant_role_code").value("app_leave_leave_applicant"))
                .andExpect(jsonPath("$.roles[0].created").value(true))
                .andExpect(jsonPath("$.roles[0].grants_added").value(1));
        mvc.perform(post("/api/v1/permission/role-packs/apply").contentType(MediaType.APPLICATION_JSON).content(CAMEL)
                        .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].created").value(false))
                .andExpect(jsonPath("$.roles[0].grants_added").value(1));
    }

    @Test
    void aPackGrantingForeignResourcesIsABadRequest() throws Exception {
        String[] admin = login("pack-bad-", true);
        mvc.perform(post("/api/v1/permission/role-packs/apply").contentType(MediaType.APPLICATION_JSON)
                        .content(SNAKE.replace("fn.leave.leave_apply", "permission.role"))
                        .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCallerWithoutAContextIsRefused() throws Exception {
        mvc.perform(post("/api/v1/permission/role-packs/apply").contentType(MediaType.APPLICATION_JSON).content(SNAKE))
                .andExpect(status().is4xxClientError());
    }
}
