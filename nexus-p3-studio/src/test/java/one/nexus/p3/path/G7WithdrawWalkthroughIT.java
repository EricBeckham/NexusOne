package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.HashChainVerification;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p3.flow.EmbeddedFlowSupport;
import one.nexus.p3.infra.isolation.P3IsolationFilter;
import one.nexus.p3.studio.api.FormIsolationFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * G7 remainder evidence: dual-tenant HTTP walkthrough of <strong>withdraw</strong>
 * (prefer travel-shaped ledger) and <strong>reject</strong> on the existing G18
 * path. This is not a new business class — no catalog / provisioner is added.
 * If PR #69's {@code /api/v1/p3/business-types/{code}/provision} is present,
 * that route is used; otherwise M0-06/07 application + ledger + form APIs.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g18")
class G7WithdrawWalkthroughIT {
    private static final UUID TENANT_A = UUID.fromString("0a0a0a0a-0a0a-4a0a-8a0a-0a0a0a0a0a01");
    private static final UUID TENANT_B = UUID.fromString("0b0b0b0b-0b0b-4b0b-8b0b-0b0b0b0b0b02");
    private static final String CODE_A = "g7wa";
    private static final String CODE_B = "g7wb";
    private static final UUID ACTOR_A = UUID.fromString("0a0a0a0a-0a0a-4a0a-8a0a-0a0a0a0a0a11");
    private static final UUID ACTOR_B = UUID.fromString("0b0b0b0b-0b0b-4b0b-8b0b-0b0b0b0b0b12");
    private static final String WITHDRAW_TRACE = "0199a1c0-0000-7000-8000-00000000b071";
    private static final String REJECT_TRACE = "0199a1c0-0000-7000-8000-00000000b072";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityModule identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditPort auditPort;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedFlowSupport.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedFlowSupport.register(registry, postgres);
    }

    @Test
    void travelWithdrawAndRejectStayOnTenant() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_A, "G7 Walk A", "g7-walk-a@example.com", "g7-walk-a"));
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT_B, "G7 Walk B", "g7-walk-b@example.com", "g7-walk-b"));
        Session adminA = login("g7-walk-a", "g7-walk-a@example.com", "G7 Walk A");
        Session adminB = login("g7-walk-b", "g7-walk-b@example.com", "G7 Walk B");
        Member reviewerA = provisionMember(adminA, "g7-walk-ra", "g7-walk-ra@example.com", "G7 Walk Reviewer A");

        JsonNode travelA = prepareTravel(TENANT_A, CODE_A, ACTOR_A, "wda");
        JsonNode travelB = prepareTravel(TENANT_B, CODE_B, ACTOR_B, "wdb");
        UUID formA = UUID.fromString(travelA.path("form_id").asText());
        UUID ledgerA = UUID.fromString(travelA.path("ledger_id").asText());
        String tableA = travelA.path("physical_table").asText();
        assertTrue(tableA.contains("travel"), tableA);
        assertTrue(travelB.path("physical_table").asText().contains("travel"));

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formA, reviewerA.personId,
                                "上海出差撤回", "上海", 2, "客户拜访")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode startJson = mapper.readTree(started);
        UUID recordId = UUID.fromString(startJson.path("record_id").asText());
        String instanceId = startJson.path("process").path("id").asText();
        String todoId = startJson.path("todo").path("id").asText();

        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(instanceId))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewerA.session.token)
                        .header("X-Nexus-Context-Id", reviewerA.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(todoId));
        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + adminB.token)
                        .header("X-Nexus-Context-Id", adminB.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + adminB.token)
                        .header("X-Nexus-Context-Id", adminB.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P3.FLOW.0002"));
        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + reviewerA.session.token)
                        .header("X-Nexus-Context-Id", reviewerA.session.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P3.FLOW.0006"));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.process.status").value("WITHDRAWN"));
        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"));

        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "select approval_status from business_data." + tableA + " where id = ?",
                String.class,
                recordId));
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerA + "/records/" + recordId)
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.destination").value("上海"));
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerA + "/records/" + recordId)
                        .header("Authorization", "Bearer " + adminB.token)
                        .header("X-Nexus-Context-Id", adminB.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));

        List<ChainedAuditEvent> withdrawn = auditPort.findByTraceId(WITHDRAW_TRACE);
        assertTrue(withdrawn.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())),
                withdrawn.toString());
        assertTrue(withdrawn.stream().anyMatch(event -> "p3.approval.started".equals(event.event().eventType())),
                withdrawn.toString());
        assertTrue(withdrawn.stream().anyMatch(event -> "p3.approval.withdrawn".equals(event.event().eventType())),
                withdrawn.toString());

        String rejectStarted = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", REJECT_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formA, reviewerA.personId,
                                "北京出差驳回", "北京", 1, "培训")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode rejectJson = mapper.readTree(rejectStarted);
        UUID rejectRecord = UUID.fromString(rejectJson.path("record_id").asText());
        String rejectTodo = rejectJson.path("todo").path("id").asText();

        mvc.perform(post("/api/v1/p3/path/tasks/" + rejectTodo + "/reject")
                        .header("Authorization", "Bearer " + adminB.token)
                        .header("X-Nexus-Context-Id", adminB.contextId)
                        .header("X-Trace-Id", REJECT_TRACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P3.FLOW.0002"));
        mvc.perform(post("/api/v1/p3/path/tasks/" + rejectTodo + "/reject")
                        .header("Authorization", "Bearer " + reviewerA.session.token)
                        .header("X-Nexus-Context-Id", reviewerA.session.contextId)
                        .header("X-Trace-Id", REJECT_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("REJECTED"))
                .andExpect(jsonPath("$.process.status").value("REJECTED"));
        mvc.perform(post("/api/v1/p3/path/tasks/" + rejectTodo + "/reject")
                        .header("Authorization", "Bearer " + reviewerA.session.token)
                        .header("X-Nexus-Context-Id", reviewerA.session.contextId)
                        .header("X-Trace-Id", REJECT_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("REJECTED"));

        assertEquals("REJECTED", jdbc.queryForObject(
                "select approval_status from business_data." + tableA + " where id = ?",
                String.class,
                rejectRecord));
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerA + "/records/" + rejectRecord)
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", REJECT_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("REJECTED"))
                .andExpect(jsonPath("$.destination").value("北京"));
        mvc.perform(get("/api/v1/todos/done")
                        .header("Authorization", "Bearer " + reviewerA.session.token)
                        .header("X-Nexus-Context-Id", reviewerA.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rejectTodo));

        List<ChainedAuditEvent> rejected = auditPort.findByTraceId(REJECT_TRACE);
        assertTrue(rejected.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())),
                rejected.toString());
        assertTrue(rejected.stream().anyMatch(event -> "p3.approval.rejected".equals(event.event().eventType())),
                rejected.toString());
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + adminA.token)
                        .header("X-Nexus-Context-Id", adminA.contextId)
                        .header("X-Trace-Id", REJECT_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        HashChainVerification chain = auditPort.verifyChain();
        assertTrue(chain.intact(), chain.failure());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_meta.process_instance where tenant_id = ?",
                Integer.class,
                TENANT_B));
    }

    private JsonNode prepareTravel(UUID tenant, String tenantCode, UUID actor, String suffix) throws Exception {
        MvcResult catalog = mvc.perform(post("/api/v1/p3/business-types/travel/provision")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publish\":true,\"suffix\":\"" + suffix + "\"}"))
                .andReturn();
        if (catalog.getResponse().getStatus() == 201) {
            JsonNode body = mapper.readTree(catalog.getResponse().getContentAsString());
            assertEquals("travel", body.path("type_code").asText());
            return body;
        }
        return prepareTravelViaExistingStack(tenant, tenantCode, actor, suffix);
    }

    private JsonNode prepareTravelViaExistingStack(UUID tenant, String tenantCode, UUID actor, String suffix)
            throws Exception {
        String appJson = mvc.perform(post("/api/v1/p3/applications")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"hr%s","display_name":"差旅","kind":"CUSTOM"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID appId = UUID.fromString(mapper.readTree(appJson).path("id").asText());
        String ledgerJson = mvc.perform(post("/api/v1/p3/applications/" + appId + "/ledgers")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ledger_code":"travel","display_name":"出差申请",
                                 "fields":[
                                   {"field_code":"title","display_name":"标题","field_type":"TEXT","required":true},
                                   {"field_code":"destination","display_name":"目的地","field_type":"TEXT","required":true},
                                   {"field_code":"days","display_name":"天数","field_type":"INTEGER","required":true},
                                   {"field_code":"purpose","display_name":"事由","field_type":"TEXT","required":true},
                                   {"field_code":"applicant","display_name":"申请人","field_type":"TEXT","required":true},
                                   {"field_code":"approval_status","display_name":"审批状态","field_type":"TEXT","required":false}
                                 ]}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ledger = mapper.readTree(ledgerJson);
        String formJson = mvc.perform(post("/api/v1/p3/forms")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"hr%s","form_code":"travel_form%s","name":"出差申请",
                                 "bindings":[
                                   {"widget_code":"title","ledger_code":"travel","field_code":"title","required":true},
                                   {"widget_code":"destination","ledger_code":"travel","field_code":"destination","required":true},
                                   {"widget_code":"days","ledger_code":"travel","field_code":"days","required":true,"widget_type":"NUMBER"},
                                   {"widget_code":"purpose","ledger_code":"travel","field_code":"purpose","required":true,"widget_type":"TEXTAREA"},
                                   {"widget_code":"applicant","ledger_code":"travel","field_code":"applicant","required":true,"widget_type":"PERSON"}
                                 ]}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID formId = UUID.fromString(mapper.readTree(formJson).path("form_id").asText());
        mvc.perform(post("/api/v1/p3/forms/" + formId + "/validate")
                        .headers(demoHeaders(tenant, tenantCode, actor)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/p3/forms/" + formId + "/publish")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"g7-walk\"}"))
                .andExpect(status().isOk());
        return mapper.readTree("""
                {"form_id":"%s","physical_table":"%s","ledger_id":"%s","type_code":"travel"}
                """.formatted(formId, ledger.path("physical_table").asText(), ledger.path("id").asText()));
    }

    private static String submitBody(UUID formId, UUID assignee, String title, String destination, int days, String purpose) {
        return """
                {"form_id":"%s","process_key":"nexusSequentialApproval",
                 "assignee_type":"PERSON","assignee_ref":"%s",
                 "values":{"title":"%s","destination":"%s","days":%d,"purpose":"%s","applicant":"alice"}}
                """.formatted(formId, assignee, title, destination, days, purpose);
    }

    private static HttpHeaders demoHeaders(UUID tenant, String code, UUID actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, code);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000b070");
        return headers;
    }

    private Session login(String username, String email, String name) throws Exception {
        String token = StubIdentityProvider.token(username, email, username, name);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contextId = extract(login.getResponse().getContentAsString(), "context_id");
        if (contextId == null || "null".equals(contextId)) {
            contextId = extract(login.getResponse().getContentAsString(), "contextId");
        }
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
                        .content("{\"access_token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
        var account = identity.repository().findAccountByEmail(email).orElseThrow();
        mvc.perform(post("/api/v1/memberships")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account_id\":\"" + account.id() + "\"}"))
                .andExpect(status().isOk());
        MvcResult created = mvc.perform(post("/api/v1/persons")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"display_name\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"account_id\":\"" + account.id() + "\"}"))
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
