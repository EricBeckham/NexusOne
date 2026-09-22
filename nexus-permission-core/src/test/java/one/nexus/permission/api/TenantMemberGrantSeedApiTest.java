package one.nexus.permission.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Browser-shaped path: stub login + issued TenantContext → permission snapshot
 * and {@code ledger/read} check. Product default seeds {@code tenant_member}
 * onto the current <em>person id</em>, not position code {@code TENANT_ADMIN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexus.permission.seed-tenant-member-grants=true")
class TenantMemberGrantSeedApiTest {
    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;
    @Autowired TenantMemberGrantSeeder seeder;

    @Test
    void stubLoginThenPermissionSnapshotHasGrantsAndLedgerReadIsAllowed() throws Exception {
        assertNotNull(seeder, "product default must register TenantMemberGrantSeeder");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "Browser Admin", "browser-admin@example.com", "browser-admin"));
        var person = identity.repository().findPersonByAccount(TENANT,
                identity.repository().findAccountByEmail("browser-admin@example.com").orElseThrow().id())
                .orElseThrow();
        String token = StubIdentityProvider.token(
                "browser-admin", "browser-admin@example.com", "browser-admin", "Browser Admin");
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
                            .content("{\"tenantId\":\"" + TENANT + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            contextId = extract(switched.getResponse().getContentAsString(), "contextId");
        }

        mvc.perform(get("/api/v1/permission/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/v1/permission/snapshot")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_count").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.not("no_grant")));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ledger\",\"action\":\"write\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"form\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"process.task\",\"action\":\"approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"process.task\",\"action\":\"reject\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"process.instance\",\"action\":\"withdraw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        assertTrue(permission.repository().findRoleByCode(TENANT, TenantMemberGrantSeeder.ROLE_CODE).isPresent());
        long personAssignments = permission.repository().listAssignments(TENANT).stream()
                .filter(item -> "user".equals(item.principalType())
                        && person.id().toString().equals(item.principalId())
                        && TenantMemberGrantSeeder.ROLE_CODE.equals(item.roleCode()))
                .count();
        assertEquals(1, personAssignments);
        assertTrue(permission.repository().listAssignments(TENANT).stream()
                .noneMatch(item -> "TENANT_ADMIN".equals(item.principalId())
                        || "position".equals(item.principalType()) && "TENANT_ADMIN".equals(item.roleCode())));
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
