package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.permission.PermissionModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** PLA1: position ↔ role binding over HTTP, behind the PLA0 guard. */
@SpringBootTest
@AutoConfigureMockMvc
class PositionRoleApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    UUID tenant;
    String token;
    String contextId;
    UUID position;
    UUID role;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        String sub = "pos-admin-" + tenant.toString().substring(0, 8);
        var admin = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Pos Admin", sub + "@example.com", sub));
        token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        var view = identity.sessions().loginWithAccessToken(token);
        contextId = view.contextId().toString();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, admin.accountId(), admin.personId(), sub,
                Instant.now(), Instant.now().plusSeconds(60)));
        position = identity.organization().createPosition(
                new OrganizationService.PositionCommand("clerk", "文员", "GENERAL", null)).id();
        role = permission.admin().createRole("clerk_role", "文员角色", false).id();
        TenantContextHolder.clear();
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    ResultActions call(MockHttpServletRequestBuilder request, String bearer, String context) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Nexus-Context-Id", context)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void theTenantAdminBindsAndListsPositionRolesWithEitherFieldSpelling() throws Exception {
        call(put("/api/v1/permission/positions/" + position + "/roles").content("{\"role_ids\":[\"" + role + "\"]}"),
                token, contextId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role_code").value("clerk_role"))
                .andExpect(jsonPath("$[0].role_name").value("文员角色"));
        call(put("/api/v1/permission/positions/" + position + "/roles").content("{\"roleIds\":[\"" + role + "\"]}"),
                token, contextId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/permission/positions/roles"), token, contextId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position_id").value(position.toString()))
                .andExpect(jsonPath("$[0].roles[0].role_id").value(role.toString()));
        call(get("/api/v1/permission/positions/" + position + "/roles"), token, contextId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role_code").value("clerk_role"));
    }

    @Test
    void anUnknownPositionIsNotFound() throws Exception {
        call(put("/api/v1/permission/positions/" + UUID.randomUUID() + "/roles").content("{\"role_ids\":[]}"),
                token, contextId)
                .andExpect(status().isNotFound());
    }

    @Test
    void someoneWithoutAccessIsRefused() throws Exception {
        String outsider = "pos-outsider-" + tenant.toString().substring(0, 8);
        String outsiderToken = StubIdentityProvider.token(outsider, outsider + "@example.com", outsider, outsider);
        String otherTenantAdmin = "pos-other-" + tenant.toString().substring(0, 8);
        UUID otherTenant = UUID.randomUUID();
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                otherTenant, "Other", otherTenantAdmin + "@example.com", otherTenantAdmin));
        String otherToken = StubIdentityProvider.token(otherTenantAdmin, otherTenantAdmin + "@example.com",
                otherTenantAdmin, otherTenantAdmin);
        String otherContext = identity.sessions().loginWithAccessToken(otherToken).contextId().toString();
        TenantContextHolder.clear();
        // An admin of another tenant cannot touch this tenant's position (it does not exist in their tenant).
        call(put("/api/v1/permission/positions/" + position + "/roles").content("{\"role_ids\":[\"" + role + "\"]}"),
                otherToken, otherContext)
                .andExpect(status().isNotFound());
        // A token without a context in this tenant is refused before anything is read.
        call(get("/api/v1/permission/positions/roles"), outsiderToken, contextId)
                .andExpect(status().is4xxClientError());
    }
}
