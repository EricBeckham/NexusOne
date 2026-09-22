package one.nexus.p2;

import one.nexus.p2.idp.StubIdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void nonIamRoutesAreNotCapturedByIdentityFilter() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientTenantHeaderIsRejected() throws Exception {
        String token = StubIdentityProvider.token("api-alice", "api-alice@example.com", "api-alice", "Api Alice");
        mvc.perform(get("/api/v1/auth/session")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P2.IAM.CLIENT_TENANT_REJECTED"));
    }

    @Test
    void loginSwitchAndOrgStayOnIssuedContext() throws Exception {
        UUID tenantA = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID tenantB = UUID.fromString("22222222-2222-4222-8222-222222222222");
        mvc.perform(post("/api/v1/internal/tenants/" + tenantA + "/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Admin A\",\"email\":\"api-admin-a@example.com\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/internal/tenants/" + tenantA + "/admin-users")
                        .header("X-Nexus-Bootstrap-Token", "test-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Admin A\",\"email\":\"api-admin-a@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantA.toString()));
        module.adminInitializer().initialize(new one.nexus.p2.admin.TenantAdminInitializer.Command(
                tenantB, "Admin B", "api-admin-b@example.com", "api-admin-b"));

        String token = StubIdentityProvider.token("api-carol", "carol@example.com", "carol", "Carol");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextId").value(nullValue()));

        var carol = module.repository().findAccountByEmail("carol@example.com").orElseThrow();
        var adminA = module.repository().findAccountByEmail("api-admin-a@example.com").orElseThrow();
        var personA = module.repository().findPersonByAccount(tenantA, adminA.id()).orElseThrow();
        one.nexus.p2.kernel.TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(
                UUID.randomUUID(), tenantA, adminA.id(), personA.id(), "tmp",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)));
        module.memberships().add(carol.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Carol A", "C-A", "carol@example.com", null, null, carol.id()));
        one.nexus.p2.kernel.TenantContextHolder.clear();

        var adminB = module.repository().findAccountByEmail("api-admin-b@example.com").orElseThrow();
        var personB = module.repository().findPersonByAccount(tenantB, adminB.id()).orElseThrow();
        one.nexus.p2.kernel.TenantContextHolder.set(new one.nexus.p2.kernel.TenantContext(
                UUID.randomUUID(), tenantB, adminB.id(), personB.id(), "tmp",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)));
        module.memberships().add(carol.id());
        module.organization().createPerson(new one.nexus.p2.org.OrganizationService.PersonCommand(
                "Carol B", "C-B", "carol@example.com", null, null, carol.id()));
        one.nexus.p2.kernel.TenantContextHolder.clear();

        MvcResult switched = mvc.perform(post("/api/v1/auth/tenant-switch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenantA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantA.toString()))
                .andReturn();
        String body = switched.getResponse().getContentAsString();
        String contextId = one.nexus.p2.idp.StubIdentityProvider.field(body, "contextId");

        mvc.perform(get("/api/v1/persons")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName=='Carol A')]").exists())
                .andExpect(jsonPath("$[?(@.displayName=='Carol B')]").doesNotExist());

        mvc.perform(get("/api/v1/internal/identity/reconcile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/identity/reconcile")
                        .header("X-Nexus-Bootstrap-Token", "test-bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("identity.reconcile"))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.matched").isNumber());

        mvc.perform(get("/api/v1/identity/reconcile")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmatchedIdp").value(0))
                .andExpect(jsonPath("$.findings[?(@.tenantId=='" + tenantB + "')]").doesNotExist())
                .andExpect(jsonPath("$.findings[?(@.tenantId=='" + tenantA + "')]").exists());

        var drifted = module.repository().findPersonByAccount(tenantA, carol.id()).orElseThrow();
        module.repository().savePerson(new one.nexus.p2.domain.Model.Person(
                drifted.id(), drifted.tenantId(), drifted.accountId(), drifted.displayName(), drifted.employeeNo(),
                "stale-carol@example.com", drifted.phone(), drifted.hiredOn(), drifted.status(),
                drifted.createdAt(), java.time.Instant.now(), drifted.deletedAt(), drifted.version() + 1));
        mvc.perform(post("/api/v1/identity/reconcile")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apply\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.repaired").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(
                "carol@example.com",
                module.repository().findPerson(tenantA, drifted.id()).orElseThrow().email());
    }
}
