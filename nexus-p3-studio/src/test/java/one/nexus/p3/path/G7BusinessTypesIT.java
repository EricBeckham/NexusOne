package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * G7 remainder: three distinct catalog types on the existing path.
 * Leave approve, expense reject, travel withdraw — plus query / todo / notify / audit.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g7")
class G7BusinessTypesIT {
    private static final UUID TENANT = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String TENANT_CODE = "g7a";
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000a077";
    private static final String WITHDRAW_TRACE = "0199a1c0-0000-7000-8000-00000000a078";
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
    void catalogHasThreeDistinctTypes() throws Exception {
        mvc.perform(get("/api/v1/p3/business-types")
                        .headers(demoHeaders(TENANT, TENANT_CODE, UUID.fromString("77777777-7777-4777-8777-777777777701"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.items[0].code").value("leave"))
                .andExpect(jsonPath("$.items[1].code").value("expense"))
                .andExpect(jsonPath("$.items[2].code").value("travel"))
                .andExpect(jsonPath("$.items[1].fields[1].field_code").value("amount"))
                .andExpect(jsonPath("$.items[2].fields[1].field_code").value("destination"));
    }

    @Test
    void threeTypesInitiateHandleQueryNotifyAndAudit() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G7 Admin", "g7-admin@example.com", "g7-admin"));
        Session admin = login("g7-admin", "g7-admin@example.com", "G7 Admin");
        Member reviewer = provisionMember(admin, "g7-reviewer", "g7-reviewer@example.com", "G7 Reviewer");

        JsonNode leave = runType(
                "leave",
                "g7l",
                admin,
                reviewer,
                TRACE,
                Handle.APPROVE,
                "p3.approval.approved",
                "APPROVED");
        JsonNode expense = runType(
                "expense",
                "g7e",
                admin,
                reviewer,
                "0199a1c0-0000-7000-8000-00000000a079",
                Handle.REJECT,
                "p3.approval.rejected",
                "REJECTED");
        JsonNode travel = runType(
                "travel",
                "g7t",
                admin,
                reviewer,
                "0199a1c0-0000-7000-8000-00000000a07a",
                Handle.WITHDRAW,
                "p3.approval.withdrawn",
                "WITHDRAWN");

        assertTrue(leave.path("physical_table").asText().contains("leave"));
        assertTrue(expense.path("physical_table").asText().contains("expense"));
        assertTrue(travel.path("physical_table").asText().contains("travel"));
        HashChainVerification chain = auditPort.verifyChain();
        assertTrue(chain.intact(), chain.failure());
    }

    @Test
    void travelWithdrawIsIdempotentAndLeavesAuditEvidence() throws Exception {
        UUID tenant = UUID.fromString("88888888-8888-4888-8888-888888888888");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G7 Withdraw", "g7-withdraw@example.com", "g7-withdraw"));
        Session admin = login("g7-withdraw", "g7-withdraw@example.com", "G7 Withdraw");
        Member reviewer = provisionMember(admin, "g7-wd-reviewer", "g7-wd-reviewer@example.com", "G7 WD Reviewer");
        JsonNode prepared = provision("travel", "g7w", tenant, "g7b", UUID.fromString("88888888-8888-4888-8888-888888888801"));
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        Map<String, Object> values = sampleValues(prepared);

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formId, reviewer.personId, values)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode startJson = mapper.readTree(started);
        UUID recordId = UUID.fromString(startJson.path("record_id").asText());
        String instanceId = startJson.path("process").path("id").asText();
        String todoId = startJson.path("todo").path("id").asText();

        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(instanceId))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
        mvc.perform(get("/api/v1/todos/pending")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(todoId));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.process.status").value("WITHDRAWN"));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.process.status").value("WITHDRAWN"));

        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));
        mvc.perform(get("/api/v1/p3/ledgers/" + prepared.path("ledger_id").asText() + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.destination").value("上海"));

        List<ChainedAuditEvent> events = auditPort.findByTraceId(WITHDRAW_TRACE);
        assertTrue(events.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())), events.toString());
        assertTrue(events.stream().anyMatch(event -> "p3.approval.started".equals(event.event().eventType())), events.toString());
        assertTrue(events.stream().anyMatch(event -> "p3.approval.withdrawn".equals(event.event().eventType())), events.toString());
        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", WITHDRAW_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        HashChainVerification chain = auditPort.verifyChain();
        assertTrue(chain.intact(), chain.failure());
    }

    private JsonNode runType(
            String type,
            String suffix,
            Session admin,
            Member reviewer,
            String trace,
            Handle handle,
            String auditType,
            String expectedStatus) throws Exception {
        JsonNode prepared = provision(type, suffix, TENANT, TENANT_CODE, UUID.fromString("77777777-7777-4777-8777-777777777701"));
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        UUID ledgerId = UUID.fromString(prepared.path("ledger_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        Map<String, Object> values = sampleValues(prepared);

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", trace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formId, reviewer.personId, values)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andExpect(jsonPath("$.ledger_code").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode startJson = mapper.readTree(started);
        UUID recordId = UUID.fromString(startJson.path("record_id").asText());
        String todoId = startJson.path("todo").path("id").asText();
        String instanceId = startJson.path("process").path("id").asText();

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from business_data." + physicalTable
                        + " where id = ? and tenant_id = ? and deleted_at is null",
                Integer.class,
                recordId,
                TENANT.toString()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from platform.exec_outbox where destination = 'notify.inbox' and trace_id = ?",
                Integer.class,
                trace));

        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", trace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"));

        switch (handle) {
            case APPROVE -> mvc.perform(post("/api/v1/p3/path/tasks/" + todoId + "/approve")
                            .header("Authorization", "Bearer " + reviewer.session.token)
                            .header("X-Nexus-Context-Id", reviewer.session.contextId)
                            .header("X-Trace-Id", trace))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approval_status").value("APPROVED"));
            case REJECT -> mvc.perform(post("/api/v1/p3/path/tasks/" + todoId + "/reject")
                            .header("Authorization", "Bearer " + reviewer.session.token)
                            .header("X-Nexus-Context-Id", reviewer.session.contextId)
                            .header("X-Trace-Id", trace))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approval_status").value("REJECTED"));
            case WITHDRAW -> mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                            .header("Authorization", "Bearer " + admin.token)
                            .header("X-Nexus-Context-Id", admin.contextId)
                            .header("X-Trace-Id", trace))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"));
        }

        assertEquals(expectedStatus, jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));
        List<ChainedAuditEvent> events = auditPort.findByTraceId(trace);
        assertTrue(events.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())), events.toString());
        assertTrue(events.stream().anyMatch(event -> auditType.equals(event.event().eventType())), events.toString());
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", trace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        return prepared;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sampleValues(JsonNode prepared) {
        JsonNode samples = prepared.path("sample_values");
        return mapper.convertValue(samples, LinkedHashMap.class);
    }

    private String submitBody(UUID formId, UUID assignee, Map<String, Object> values) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("form_id", formId.toString());
        body.put("process_key", "nexusSequentialApproval");
        body.put("assignee_type", "PERSON");
        body.put("assignee_ref", assignee.toString());
        body.put("values", values);
        return mapper.writeValueAsString(body);
    }

    private JsonNode provision(String type, String suffix, UUID tenant, String tenantCode, UUID actor) throws Exception {
        String created = mvc.perform(post("/api/v1/p3/business-types/" + type + "/provision")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publish\":true,\"suffix\":\"" + suffix + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type_code").value(type))
                .andExpect(jsonPath("$.form_id").exists())
                .andExpect(jsonPath("$.physical_table").exists())
                .andExpect(jsonPath("$.running_version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(created);
    }

    private static org.springframework.http.HttpHeaders demoHeaders(UUID tenant, String code, UUID actor) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, code);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a076");
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

    private enum Handle { APPROVE, REJECT, WITHDRAW }

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
