package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The product mapper is snake_case while the permission page sends camelCase; a body in the other spelling arrived
 * as nulls and the grant write answered 500 (found in the PLA9 acceptance). Both spellings must work.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionWireSpellingApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    String[] admin() {
        UUID tenant = UUID.randomUUID();
        String sub = "wire-" + tenant.toString().substring(0, 8);
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Wire", sub + "@example.com", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String context = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.clear();
        return new String[] {token, context};
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"effect\":\"GRANT\",\"principal_type\":\"user\",\"principal_id\":\"p-1\",\"resource\":\"report\",\"action\":\"read\",\"scope_kind\":\"SELF\",\"specified_departments\":[]}",
        "{\"effect\":\"GRANT\",\"principalType\":\"user\",\"principalId\":\"p-1\",\"resource\":\"report\",\"action\":\"read\",\"scopeKind\":\"SELF\",\"specifiedDepartments\":[]}"
    })
    void aGrantIsAcceptedInEitherSpelling(String body) throws Exception {
        String[] admin = admin();
        mvc.perform(post("/api/v1/permission/grants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                .andExpect(status().isOk());
    }

    @Test
    void anAssignmentIsAcceptedInEitherSpelling() throws Exception {
        String[] admin = admin();
        String roleCreated = mvc.perform(post("/api/v1/permission/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wire_role\",\"name\":\"wire\",\"system_role\":false}")
                        .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String roleId = roleCreated.replaceAll("(?s).*\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
        for (String body : new String[] {
                "{\"principal_type\":\"user\",\"principal_id\":\"p-1\",\"role_id\":\"" + roleId + "\"}",
                "{\"principalType\":\"user\",\"principalId\":\"p-2\",\"roleId\":\"" + roleId + "\"}"}) {
            mvc.perform(post("/api/v1/permission/assignments").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("Authorization", "Bearer " + admin[0]).header("X-Nexus-Context-Id", admin[1]))
                    .andExpect(status().isOk());
        }
    }
}
