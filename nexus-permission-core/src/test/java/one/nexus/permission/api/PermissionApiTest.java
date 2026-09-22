package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.permission.PermissionModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule identity;

    @Autowired
    PermissionModule permission;

    @Test
    void healthIsPublicAndClientTenantIsRejected() throws Exception {
        mvc.perform(get("/api/v1/permission/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module").value("nexus-permission-core"));
        String token = StubIdentityProvider.token("perm-api", "perm-api@example.com", "perm-api", "Perm Api");
        mvc.perform(get("/api/v1/permission/snapshot")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P2.PERM.0013"));
    }

    @Test
    void checkUsesIssuedContextAndDeniesWithoutGrant() throws Exception {
        UUID tenantA = UUID.fromString("11111111-1111-4111-8111-111111111111");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantA, "Perm Admin", "perm-admin@example.com", "perm-admin"));
        var admin = identity.repository().findPersonByAccount(tenantA,
                identity.repository().findAccountByEmail("perm-admin@example.com").orElseThrow().id()).orElseThrow();
        String token = StubIdentityProvider.token("perm-admin", "perm-admin@example.com", "perm-admin", "Perm Admin");
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // context may be auto-issued for single membership
        String body = login.getResponse().getContentAsString();
        String contextId = extract(body, "contextId");
        if (contextId == null || contextId.equals("null")) {
            MvcResult switched = mvc.perform(post("/api/v1/auth/tenant-switch")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantA + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            contextId = extract(switched.getResponse().getContentAsString(), "contextId");
        }

        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("no_grant"));

        MvcResult role = mvc.perform(post("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"reader\",\"name\":\"Reader\",\"systemRole\":false}"))
                .andExpect(status().isOk())
                .andReturn();
        String roleId = extract(role.getResponse().getContentAsString(), "id");
        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"user\",\"principalId\":\"" + admin.id()
                                + "\",\"roleId\":\"" + roleId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/permission/grants")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"GRANT\",\"principalType\":\"role\",\"principalId\":\"reader\","
                                + "\"resource\":\"ledger\",\"action\":\"read\",\"scopeKind\":\"SELF\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("allowed"));
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
        int end = json.indexOf("\"", from);
        return json.substring(from, end);
    }
}
