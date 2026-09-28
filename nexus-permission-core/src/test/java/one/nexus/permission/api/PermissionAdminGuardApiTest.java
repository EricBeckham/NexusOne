package one.nexus.permission.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import one.nexus.permission.engine.DataScopeKind;
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

/** PLA0: the permission admin API refuses ordinary members; admins and explicitly granted people may manage it. */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionAdminGuardApiTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;

    UUID tenant;
    Session admin;
    Session member;

    record Session(String token, String contextId, UUID personId) {}

    @BeforeEach
    void setUp() throws Exception {
        tenant = UUID.randomUUID();
        String suffix = tenant.toString().substring(0, 8);
        var adminPerson = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Guard Admin", "guard-admin-" + suffix + "@example.com", "guard-admin-" + suffix));
        admin = login("guard-admin-" + suffix);

        // An ordinary member: ACTIVE membership + person, no admin position, no grants.
        String memberSub = "guard-member-" + suffix;
        String memberToken = StubIdentityProvider.token(memberSub, memberSub + "@example.com", memberSub, "Member");
        identity.sessions().loginWithAccessToken(memberToken);
        var account = identity.repository().findAccountByEmail(memberSub + "@example.com").orElseThrow();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, adminPerson.accountId(),
                adminPerson.personId(), "guard-admin-" + suffix, Instant.now(), Instant.now().plusSeconds(60)));
        identity.memberships().add(account.id());
        identity.organization().createPerson(new OrganizationService.PersonCommand(
                "Member", "M-1", memberSub + "@example.com", null, null, account.id()));
        TenantContextHolder.clear();
        member = login(memberSub);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    Session login(String sub) throws Exception {
        String token = StubIdentityProvider.token(sub, sub + "@example.com", sub, sub);
        var view = identity.sessions().loginWithAccessToken(token);
        TenantContextHolder.clear();
        return new Session(token, view.contextId().toString(), view.personId());
    }

    ResultActions as(Session who, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + who.token())
                .header("X-Nexus-Context-Id", who.contextId())
                .contentType(MediaType.APPLICATION_JSON));
    }

    String roleId(Session who) throws Exception {
        String body = as(who, post("/api/v1/permission/roles")
                .content("{\"code\":\"guard_role\",\"name\":\"Guard\",\"systemRole\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void anOrdinaryMemberCannotChangeOrListThePolicy() throws Exception {
        String role = roleId(admin);
        as(member, post("/api/v1/permission/assignments")
                .content("{\"principalType\":\"user\",\"principalId\":\"" + member.personId() + "\",\"roleId\":\"" + role + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        as(member, post("/api/v1/permission/roles").content("{\"code\":\"self_made\",\"name\":\"x\",\"systemRole\":false}"))
                .andExpect(status().isForbidden());
        as(member, post("/api/v1/permission/grants").content(
                "{\"effect\":\"GRANT\",\"principalType\":\"user\",\"principalId\":\"" + member.personId()
                        + "\",\"resource\":\"ledger\",\"action\":\"write\",\"scopeKind\":\"ALL\"}"))
                .andExpect(status().isForbidden());
        as(member, post("/api/v1/permission/matrix/example")).andExpect(status().isForbidden());
        as(member, post("/api/v1/permission/duties/example")).andExpect(status().isForbidden());
        for (String path : new String[] {"roles", "assignments", "grants", "matrix", "duties"}) {
            as(member, get("/api/v1/permission/" + path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void selfChecksAndSnapshotCountersStayOpen() throws Exception {
        as(member, post("/api/v1/permission/check").content("{\"resource\":\"ledger\",\"action\":\"read\"}"))
                .andExpect(status().isOk());
        as(member, get("/api/v1/permission/snapshot")).andExpect(status().isOk());
    }

    @Test
    void theTenantAdminPositionMayManageTheTenantPolicy() throws Exception {
        String role = roleId(admin);
        as(admin, post("/api/v1/permission/assignments")
                .content("{\"principalType\":\"user\",\"principalId\":\"" + member.personId() + "\",\"roleId\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        as(admin, get("/api/v1/permission/roles")).andExpect(status().isOk());
    }

    @Test
    void anExplicitAssignmentGrantIsEnoughWithoutTheAdminPosition() throws Exception {
        String role = roleId(admin);
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), tenant, null, admin.personId(), "admin",
                Instant.now(), Instant.now().plusSeconds(60)));
        try {
            var delegate = permission.admin().createRole("assignment_delegate", "Delegate", false);
            permission.admin().grant("GRANT", "role", "assignment_delegate", "permission.assignment", "write",
                    DataScopeKind.ALL, java.util.List.of(), java.util.Map.of());
            permission.admin().assign("user", member.personId().toString(), delegate.id());
        } finally {
            TenantContextHolder.clear();
        }
        as(member, post("/api/v1/permission/assignments")
                .content("{\"principalType\":\"user\",\"principalId\":\"" + member.personId() + "\",\"roleId\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        // …but not roles or grants, which need their own permission.
        as(member, post("/api/v1/permission/roles").content("{\"code\":\"other\",\"name\":\"x\",\"systemRole\":false}"))
                .andExpect(status().isForbidden());
        as(member, get("/api/v1/permission/roles")).andExpect(status().isOk());
    }
}
