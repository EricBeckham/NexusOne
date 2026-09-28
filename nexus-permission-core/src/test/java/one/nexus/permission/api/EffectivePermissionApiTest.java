package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** PLA3: effective-permission preview over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
class EffectivePermissionApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void theAdminPreviewsThemselvesAndUnknownIdsAreNotFound() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sub = "effective-" + tenant.toString().substring(0, 8);
        var admin = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(tenant, "Eff", sub + "@example.com", sub));
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        String context = identity.sessions().loginWithAccessToken(token).contextId().toString();
        TenantContextHolder.clear();

        mvc.perform(get("/api/v1/permission/effective/persons/" + admin.personId())
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject_type").value("PERSON"))
                .andExpect(jsonPath("$.duty_conflict").value(false))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.permissions").isArray());
        mvc.perform(get("/api/v1/permission/effective/positions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token).header("X-Nexus-Context-Id", context))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCallerWithoutAContextIsRefused() throws Exception {
        mvc.perform(get("/api/v1/permission/effective/persons/" + UUID.randomUUID())).andExpect(status().is4xxClientError());
    }
}
