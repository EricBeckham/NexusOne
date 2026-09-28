package one.nexus.permission.api;

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

/** PLA6: C5 readable entries over HTTP, answered for the calling member only. */
@SpringBootTest
@AutoConfigureMockMvc
class EntryAccessApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsTheReadableSubsetOfTheGivenKeys() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sub = "entry-" + tenant.toString().substring(0, 8);
        var admin = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Entry", sub + "@example.com", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String contextId = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, admin.accountId(), admin.personId(), sub,
                Instant.now(), Instant.now().plusSeconds(60)));
        permission.admin().grant("GRANT", "user", admin.personId().toString(), "fn.leave.leave_apply", "read",
                DataScopeKind.ALL, List.of(), Map.of());
        TenantContextHolder.clear();

        mvc.perform(get("/api/v1/permission/entries/readable")
                        .param("keys", "fn:leave:leave_apply", "fn:expense:expense_apply", "not-a-key")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                // PLA5 default (legacy): installed entries show unless denied; malformed keys still drop.
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0]").value("fn:leave:leave_apply"))
                .andExpect(jsonPath("$.entries[1]").value("fn:expense:expense_apply"))
                .andExpect(jsonPath("$.computed_at").exists());

        mvc.perform(get("/api/v1/permission/entries/readable")
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void refusesACallerWithoutAContext() throws Exception {
        mvc.perform(get("/api/v1/permission/entries/readable").param("keys", "fn:leave:leave_apply"))
                .andExpect(status().is4xxClientError());
    }
}
