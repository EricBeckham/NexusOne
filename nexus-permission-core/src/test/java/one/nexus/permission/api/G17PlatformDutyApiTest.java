package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.permission.admin.PlatformDutyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class G17PlatformDutyApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule identity;

    @Test
    void dutiesInstallAssignRejectAndCheckUsesSameEngine() throws Exception {
        UUID tenant = UUID.fromString("17171717-1717-4717-8717-171717171718");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G17 Api", "g17-api@example.com", "g17-api"));
        var admin = identity.repository().findPersonByAccount(tenant,
                identity.repository().findAccountByEmail("g17-api@example.com").orElseThrow().id()).orElseThrow();
        String token = StubIdentityProvider.token("g17-api", "g17-api@example.com", "g17-api", "G17 Api");
        String contextId = login(token, tenant);

        mvc.perform(get("/api/v1/permission/duties")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogInstalled").value(false))
                .andExpect(jsonPath("$.catalog[?(@.code=='" + PlatformDutyCatalog.AUDIT_ADMIN + "')]").exists())
                .andExpect(jsonPath("$.exclusivePairs").isArray());

        mvc.perform(post("/api/v1/permission/duties/example")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogInstalled").value(true))
                .andExpect(jsonPath("$.cells[?(@.principalId=='" + PlatformDutyCatalog.AUDIT_ADMIN
                        + "' && @.resource=='audit' && @.action=='read')].effect")
                        .value(org.hamcrest.Matchers.hasItem("GRANT")))
                .andExpect(jsonPath("$.cells[?(@.principalId=='" + PlatformDutyCatalog.AUDIT_ADMIN
                        + "' && @.resource=='audit' && @.action=='write')].effect")
                        .value(org.hamcrest.Matchers.hasItem("DENY")));

        String auditRoleId = extractRoleId(mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andReturn()
                .getResponse()
                .getContentAsString(), PlatformDutyCatalog.AUDIT_ADMIN);
        String systemRoleId = extractRoleId(mvc.perform(get("/api/v1/permission/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andReturn()
                .getResponse()
                .getContentAsString(), PlatformDutyCatalog.SYSTEM_ADMIN);

        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"user\",\"principalId\":\"" + admin.id()
                                + "\",\"roleId\":\"" + auditRoleId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"audit\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"audit\",\"action\":\"write\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("explicit_deny"))
                .andExpect(jsonPath("$.engine").value("PermissionEngine"));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"audit\",\"action\":\"delete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("explicit_deny"));
        mvc.perform(post("/api/v1/permission/check")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"audit\",\"action\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("explicit_deny"));

        mvc.perform(post("/api/v1/permission/assignments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"user\",\"principalId\":\"" + admin.id()
                                + "\",\"roleId\":\"" + systemRoleId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P2.PERM.0014"));
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
            return null;
        }
        int from = start + needle.length();
        return json.substring(from, json.indexOf("\"", from));
    }
}
