package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.DataScopeKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** PLA4: exception grants over HTTP — list with filters, revoke, 404 on unknown ids. */
@SpringBootTest
@AutoConfigureMockMvc
class ExceptionGrantApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void theAdminListsAndRevokesAPersonalGrant() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sub = "exc-" + tenant.toString().substring(0, 8);
        var admin = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Exc", sub + "@example.com", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String context = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, admin.accountId(), admin.personId(), sub,
                Instant.now(), Instant.now().plusSeconds(60)));
        UUID grantId = permission.admin().grant("GRANT", "user", admin.personId().toString(), "report", "read",
                DataScopeKind.SELF, List.of(), Map.of()).id();
        TenantContextHolder.clear();

        mvc.perform(get("/api/v1/permission/exceptions").param("kind", "GRANT").param("q", "report")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(grantId.toString()))
                .andExpect(jsonPath("$[0].person_id").value(admin.personId().toString()))
                .andExpect(jsonPath("$[0].system").value(false));

        mvc.perform(delete("/api/v1/permission/exceptions/grants/" + grantId)
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/permission/exceptions/grants/" + grantId)
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/permission/exceptions").param("kind", "GRANT")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCallerWithoutAContextIsRefused() throws Exception {
        mvc.perform(get("/api/v1/permission/exceptions")).andExpect(status().is4xxClientError());
    }
}
