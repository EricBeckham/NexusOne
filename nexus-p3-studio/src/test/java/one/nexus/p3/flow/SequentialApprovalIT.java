package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p3.flow.ProcessRecords.StartCommand;
import one.nexus.permission.PermissionModule;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = NexusP3FlowApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("flow")
class SequentialApprovalIT {
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;
    @Autowired RuntimeService runtimeService;
    @Autowired JdbcTemplate jdbc;
    @Autowired RollbackProbe rollbackProbe;

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedFlowSupport.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedFlowSupport.register(registry, postgres);
    }

    @Test
    void healthIsPublicAndClientTenantIsRejected() throws Exception {
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'business_meta' and table_name = 'process_instance'",
                Integer.class));
        assertTrue(jdbc.queryForList(
                "select version from platform.flyway_schema_history where success and version is not null",
                String.class).contains("550"));
        mvc.perform(get("/api/v1/process/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.work_package").value("M0-08"));
        String token = StubIdentityProvider.token("flow-api", "flow-api@example.com", "flow-api", "Flow Api");
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P3.FLOW.0006"));
    }

    @Test
    void sequentialApproveRejectWithdrawAndListsStayOnTenant() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_A, "Flow Admin A", "flow-admin-a@example.com", "flow-admin-a"));
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_B, "Flow Admin B", "flow-admin-b@example.com", "flow-admin-b"));

        Session adminA = login("flow-admin-a", "flow-admin-a@example.com", "Flow Admin A");
        Session adminB = login("flow-admin-b", "flow-admin-b@example.com", "Flow Admin B");
        Member reviewer = provisionMember(adminA, "reviewer-a", "reviewer-a@example.com", "Reviewer A");

        String businessKey = "leave-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/process/instances")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processKey\":\"nexusSequentialApproval\",\"businessKey\":\"" + businessKey
                                + "\",\"assigneeType\":\"PERSON\",\"assigneeRef\":\"" + reviewer.personId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessKey").value(businessKey));
        mvc.perform(get("/api/v1/p3/todos")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessKey").value(businessKey));
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + adminB.token)
                        .header("X-Nexus-Context-Id", adminB.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        String todoId = extract(mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andReturn().getResponse().getContentAsString(), "id");

        mvc.perform(post("/api/v1/process/tasks/" + todoId + "/approve")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/process/tasks/" + todoId + "/approve")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("APPROVE"));
        mvc.perform(post("/api/v1/process/tasks/" + todoId + "/approve")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVE"));
        mvc.perform(get("/api/v1/todos/done")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(todoId));

        Integer engineTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'flowable' and table_name like 'act_%'",
                Integer.class);
        Integer leaked = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema in ('public','platform','business_meta') and table_name like 'act_%'",
                Integer.class);
        assertTrue(engineTables != null && engineTables > 0);
        assertEquals(0, leaked);

        String rejectKey = "reject-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/process/instances")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\":\"" + rejectKey
                                + "\",\"assigneeType\":\"PERSON\",\"assigneeRef\":\"" + reviewer.personId + "\"}"))
                .andExpect(status().isOk());
        String rejectTodo = extract(mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andReturn().getResponse().getContentAsString(), "id");
        mvc.perform(post("/api/v1/process/tasks/" + rejectTodo + "/reject")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECT"));

        String withdrawKey = "withdraw-" + UUID.randomUUID();
        MvcResult withdrawStart = mvc.perform(post("/api/v1/process/instances")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\":\"" + withdrawKey
                                + "\",\"assigneeType\":\"PERSON\",\"assigneeRef\":\"" + reviewer.personId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String withdrawId = extract(withdrawStart.getResponse().getContentAsString(), "id");
        mvc.perform(post("/api/v1/process/instances/" + withdrawId + "/withdraw")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/process/instances/" + withdrawId + "/withdraw")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(post("/api/v1/process/instances/" + withdrawId + "/withdraw")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_meta.process_instance where tenant_id = ?",
                Integer.class, TENANT_B));
    }

    @Test
    void positionAndRoleAssigneesResolveThroughIamAndPermission() throws Exception {
        UUID tenant = UUID.fromString("33333333-3333-4333-8333-333333333333");
        var admin = identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Role Admin", "role-admin@example.com", "role-admin"));
        Session adminSession = login("role-admin", "role-admin@example.com", "Role Admin");
        Member reviewer = provisionMember(adminSession, "role-reviewer", "role-reviewer@example.com", "Role Reviewer");

        identity.sessions().loadContext(UUID.fromString(adminSession.contextId), "role-admin");
        var position = identity.organization().createPosition(
                new OrganizationService.PositionCommand("APPROVER", "Approver", "flow", "approve"));
        identity.organization().assign(new OrganizationService.EmploymentCommand(
                reviewer.personId, admin.departmentId(), position.id(),
                one.nexus.p2.domain.Model.EmploymentKind.PRIMARY, null, null));
        var role = permission.admin().createRole("approver", "Approver", false);
        permission.admin().assign("user", reviewer.personId.toString(), role.id());

        mvc.perform(post("/api/v1/process/instances")
                        .header("Authorization", "Bearer " + adminSession.token)
                        .header("X-Nexus-Context-Id", adminSession.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\":\"pos-" + UUID.randomUUID()
                                + "\",\"assigneeType\":\"POSITION\",\"assigneeRef\":\"" + position.id() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskName").value("Approve"));

        mvc.perform(post("/api/v1/process/instances")
                        .header("Authorization", "Bearer " + adminSession.token)
                        .header("X-Nexus-Context-Id", adminSession.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\":\"role-" + UUID.randomUUID()
                                + "\",\"assigneeType\":\"ROLE\",\"assigneeRef\":\"approver\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void startAndEngineRollBackTogether() throws Exception {
        UUID tenant = UUID.fromString("44444444-4444-4444-8444-444444444444");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Rollback Admin", "rollback-admin@example.com", "rollback-admin"));
        Session admin = login("rollback-admin", "rollback-admin@example.com", "Rollback Admin");
        identity.sessions().loadContext(UUID.fromString(admin.contextId), "rollback-admin");
        var person = identity.repository().findPersonByAccount(
                tenant, identity.repository().findAccountByEmail("rollback-admin@example.com").orElseThrow().id())
                .orElseThrow();
        String businessKey = "rollback-" + UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> rollbackProbe.startThenFail(new StartCommand(
                "nexusSequentialApproval", businessKey, AssigneeType.PERSON, person.id().toString())));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_meta.process_instance where business_key = ?",
                Integer.class, businessKey));
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count());
    }

    private Session login(String username, String email, String name) throws Exception {
        String token = StubIdentityProvider.token(username, email, username, name);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contextId = extract(login.getResponse().getContentAsString(), "contextId");
        if (contextId == null || "null".equals(contextId)) {
            throw new IllegalStateException("expected auto-issued context for " + email + ": "
                    + login.getResponse().getContentAsString());
        }
        return new Session(token, contextId);
    }

    private Member provisionMember(Session admin, String username, String email, String name) throws Exception {
        String token = StubIdentityProvider.token(username, email, username, name);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());
        var account = identity.repository().findAccountByEmail(email).orElseThrow();
        mvc.perform(post("/api/v1/memberships")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account.id() + "\"}"))
                .andExpect(status().isOk());
        MvcResult created = mvc.perform(post("/api/v1/persons")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"accountId\":\"" + account.id() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        UUID personId = UUID.fromString(extract(created.getResponse().getContentAsString(), "id"));
        return new Member(login(username, email, name), personId);
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
            start += needle.length();
            int end = json.indexOf(",", start);
            int endObj = json.indexOf("}", start);
            if (end < 0 || (endObj >= 0 && endObj < end)) {
                end = endObj;
            }
            return json.substring(start, end).replace("\"", "").trim();
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
