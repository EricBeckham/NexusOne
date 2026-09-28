package one.nexus.permission.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** PLA5: the member-access switch over HTTP; the default mode is LEGACY and conversion defaults to a dry run. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAccessApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void theAdminReadsTheModeAndConvertsWithADryRunFirst() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sub = "access-" + tenant.toString().substring(0, 8);
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Access", sub + "@example.com", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String context = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.clear();

        mvc.perform(get("/api/v1/permission/member-access")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LEGACY"));

        int before = permission.repository().listAssignments(tenant).size();
        mvc.perform(post("/api/v1/permission/member-access/convert")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dry_run").value(true))
                .andExpect(jsonPath("$.designer_positions.length()").value(1));
        assertEquals(before, permission.repository().listAssignments(tenant).size(), "a dry run writes nothing");

        mvc.perform(post("/api/v1/permission/member-access/convert").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":false}")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dry_run").value(false))
                .andExpect(jsonPath("$.seed_assignments_removed").value(0));
    }

    @Test
    void aCallerWithoutAContextIsRefused() throws Exception {
        mvc.perform(post("/api/v1/permission/member-access/convert")).andExpect(status().is4xxClientError());
    }
}
