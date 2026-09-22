package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.permission.admin.RoleMatrixCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class G2RoleMatrixApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule identity;

    @Test
    void matrixExampleIsNotTenantMemberSeedAndCheckUsesSameEngine() throws Exception {
        UUID tenant = UUID.fromString("22222222-2222-4222-8222-222222222222");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G2 Admin", "g2-admin@example.com", "g2-admin"));
        var admin = identity.repository().findPersonByAccount(tenant,
                identity.repository().findAccountByEmail("g2-admin@example.com").orElseThrow().id()).orElseThrow();
        String token = StubIdentityProvider.token("g2-admin", "g2-admin@example.com", "g2-admin", "G2 Admin");
        String contextId = login(token, tenant);

        mvc.perform(get("/api/v1/permission/matrix")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoSeedPresent").value(false))
                .andExpect(jsonPath("$.differentiatedPolicy").value(false))
                .andExpect(jsonPath("$.completenessNote").value(org.hamcrest.Matchers.containsString("tenant_member")));

        mvc.perform(post("/api/v1/permission/matrix/example")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.differentiatedPolicy").value(true))
                .andExpect(jsonPath("$.demoSeedPresent").value(false))
                .andExpect(jsonPath("$.cells[?(@.principalId=='" + RoleMatrixCatalog.SELF_READER
                        + "' && @.resource=='ledger' && @.action=='read')].scopeKind")
                        .value(org.hamcrest.Matchers.hasItem("SELF")));

        mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='" + RoleMatrixCatalog.SELF_READER + "')]").exists())
                .andExpect(jsonPath("$[?(@.code=='" + RoleMatrixCatalog.FORM_DESIGNER + "')]").exists());
        mvc.perform(get("/api/v1/permission/grants")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scopeKind=='DEPT_AND_CHILDREN')]").exists());

        String roleId = extractRoleId(mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andReturn()
                .getResponse()
                .getContentAsString(), RoleMatrixCatalog.SELF_READER);

        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("no_grant"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));

        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"user\",\"principalId\":\"" + admin.id()
                                + "\",\"roleId\":\"" + roleId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("allowed"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"))
                .andExpect(jsonPath("$.fields.salary.visible").value(false));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"process.instance\",\"action\":\"start\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("no_grant"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"form\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("allowed"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"form\",\"action\":\"write\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("no_grant"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));
    }

    private String login(String token, UUID tenant) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contextId = extract(login.getResponse().getContentAsString(), "contextId");
        if (contextId == null || "null".equals(contextId)) {
            MvcResult switched = mvc.perform(post("/api/v1/auth/tenant-switch")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenant + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            contextId = extract(switched.getResponse().getContentAsString(), "contextId");
        }
        return contextId;
    }

    private static String extractRoleId(String json, String code) {
        int codeAt = json.indexOf("\"code\":\"" + code + "\"");
        if (codeAt < 0) {
            throw new AssertionError("role " + code + " missing in " + json);
        }
        int windowStart = Math.max(0, codeAt - 180);
        String window = json.substring(windowStart, Math.min(json.length(), codeAt + 80));
        String needle = "\"id\":\"";
        int start = window.lastIndexOf(needle);
        if (start < 0) {
            start = window.indexOf(needle);
        }
        int from = start + needle.length();
        return window.substring(from, window.indexOf("\"", from));
    }

    private static String extract(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            needle = "\"" + field + "\":";
            start = json.indexOf(needle);
            if (start < 0) {
                return null;
            }
            int from = start + needle.length();
            int end = json.indexOf(",", from);
            if (end < 0) {
                end = json.indexOf("}", from);
            }
            return json.substring(from, end).replace("\"", "").trim();
        }
        int from = start + needle.length();
        return json.substring(from, json.indexOf("\"", from));
    }
}
