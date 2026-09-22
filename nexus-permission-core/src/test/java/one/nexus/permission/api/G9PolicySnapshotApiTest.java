package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP evidence that a real role grant (not {@code tenant_member}) bumps the
 * PolicySnapshot version and the next {@code /check} sees it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class G9PolicySnapshotApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule identity;

    @Test
    void grantBumpsSnapshotVersionAndCheckAllows() throws Exception {
        UUID tenant = UUID.fromString("19191919-1919-4919-8919-191919191919");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G9 Api", "g9-api@example.com", "g9-api"));
        var admin = identity.repository().findPersonByAccount(tenant,
                identity.repository().findAccountByEmail("g9-api@example.com").orElseThrow().id()).orElseThrow();
        String token = StubIdentityProvider.token("g9-api", "g9-api@example.com", "g9-api", "G9 Api");
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

        mvc.perform(get("/api/v1/permission/snapshot")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_count").value(0));

        MvcResult denied = mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("no_grant"))
                .andReturn();
        long deniedVersion = Long.parseLong(extractNumber(denied.getResponse().getContentAsString(), "policy_version"));

        MvcResult role = mvc.perform(post("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"g9_self_reader\",\"name\":\"G9 self reader\",\"systemRole\":false}"))
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
                        .content("{\"effect\":\"GRANT\",\"principalType\":\"role\",\"principalId\":\"g9_self_reader\","
                                + "\"resource\":\"ledger\",\"action\":\"read\",\"scopeKind\":\"SELF\"}"))
                .andExpect(status().isOk());

        MvcResult snapshot = mvc.perform(get("/api/v1/permission/snapshot")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_count").value(1))
                .andReturn();
        long snapshotVersion = Long.parseLong(extractNumber(snapshot.getResponse().getContentAsString(), "version"));
        assertTrue(snapshotVersion > deniedVersion, "grant must bump policy_version");

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

    private static String extractNumber(String json, String field) {
        String value = extract(json, field);
        return value == null || value.isBlank() ? "0" : value;
    }
}
