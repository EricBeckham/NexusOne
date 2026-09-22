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
 * G7 leftover: expense + travel withdraw evidence paralleling
 * {@link G7LeaveWithdrawIT}. Catalog / provisioner stay on {@code main} (#69).
 * Does not rewrite the leave IT or {@code AuditPort}. Does not claim a
 * three-type product close.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g7")
class G7ExpenseTravelWithdrawIT {
    private static final UUID EXPENSE_TENANT = UUID.fromString("77777777-7777-4777-8777-777777777711");
    private static final UUID TRAVEL_TENANT = UUID.fromString("77777777-7777-4777-8777-777777777721");
    private static final String EXPENSE_TRACE = "0199a1c0-0000-7000-8000-00000000a081";
    private static final String TRAVEL_TRACE = "0199a1c0-0000-7000-8000-00000000a082";
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
    void expenseWithdrawIsIdempotentAndLeavesAuditEvidence() throws Exception {
        withdrawCatalogType(new Case(
                "expense",
                "g7e",
                EXPENSE_TENANT,
                "g7e",
                UUID.fromString("77777777-7777-4777-8777-777777777712"),
                "g7-exp-wd",
                "g7-exp-wd@example.com",
                "G7 Expense Withdraw",
                "g7-exp-rev",
                "g7-exp-rev@example.com",
                "G7 Expense Reviewer",
                EXPENSE_TRACE,
                Map.of(
                        "title", "交通费",
                        "amount", 128.50,
                        "category", "交通",
                        "payee", "张三",
                        "remark", "g7-expense-withdraw"),
                Map.of("title", "交通费", "category", "交通", "payee", "张三", "remark", "g7-expense-withdraw")));
    }

    @Test
    void travelWithdrawIsIdempotentAndLeavesAuditEvidence() throws Exception {
        withdrawCatalogType(new Case(
                "travel",
                "g7t",
                TRAVEL_TENANT,
                "g7t",
                UUID.fromString("77777777-7777-4777-8777-777777777722"),
                "g7-trv-wd",
                "g7-trv-wd@example.com",
                "G7 Travel Withdraw",
                "g7-trv-rev",
                "g7-trv-rev@example.com",
                "G7 Travel Reviewer",
                TRAVEL_TRACE,
                Map.of(
                        "title", "客户拜访",
                        "destination", "上海",
                        "days", 3,
                        "purpose", "g7-travel-withdraw",
                        "applicant", "alice"),
                Map.of("title", "客户拜访", "destination", "上海", "purpose", "g7-travel-withdraw")));
    }

    private void withdrawCatalogType(Case spec) throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                spec.tenant(), spec.adminName(), spec.adminEmail(), spec.adminUser()));
        Session admin = login(spec.adminUser(), spec.adminEmail(), spec.adminName());
        Member reviewer = provisionMember(admin, spec.reviewerUser(), spec.reviewerEmail(), spec.reviewerName());

        JsonNode prepared = provision(spec);
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        UUID ledgerId = UUID.fromString(prepared.path("ledger_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        assertTrue(physicalTable.contains(spec.type()), physicalTable);

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formId, reviewer.personId, spec.values())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andExpect(jsonPath("$.ledger_code").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode startJson = mapper.readTree(started);
        assertTrue(startJson.path("ledger_code").asText().contains(spec.type()), startJson.toString());
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
                .andExpect(jsonPath("$[0].id").value(todoId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.process.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.record_id").value(recordId.toString()));

        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));
        var query = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"));
        for (Map.Entry<String, String> field : spec.queryFields().entrySet()) {
            query.andExpect(jsonPath("$." + field.getKey()).value(field.getValue()));
        }

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"));

        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(instanceId))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));

        List<ChainedAuditEvent> events = auditPort.findByTraceId(spec.trace());
        assertTrue(events.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> "p3.approval.started".equals(event.event().eventType())),
                events.toString());
        assertTrue(events.stream().anyMatch(event -> "p3.approval.withdrawn".equals(event.event().eventType())),
                events.toString());
        long withdrawn = events.stream()
                .filter(event -> "p3.approval.withdrawn".equals(event.event().eventType()))
                .count();
        assertEquals(1, withdrawn, "repeat withdraw must not append a second withdrawn event: " + events);
        Integer auditRows = jdbc.queryForObject(
                "select count(*) from platform.audit_record where trace_id = ?",
                Integer.class,
                spec.trace());
        assertTrue(auditRows != null && auditRows >= 3, "audit_record rows=" + auditRows + " events=" + events);
        HashChainVerification chain = auditPort.verifyChain();
        assertTrue(chain.intact(), chain.failure());

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private JsonNode provision(Case spec) throws Exception {
        String created = mvc.perform(post("/api/v1/p3/business-types/" + spec.type() + "/provision")
                        .headers(demoHeaders(spec.tenant(), spec.tenantCode(), spec.actor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publish\":true,\"suffix\":\"" + spec.suffix() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type_code").value(spec.type()))
                .andExpect(jsonPath("$.form_id").exists())
                .andExpect(jsonPath("$.physical_table").exists())
                .andExpect(jsonPath("$.running_version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(created);
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

    private static org.springframework.http.HttpHeaders demoHeaders(UUID tenant, String code, UUID actor) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, code);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a080");
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

    record Case(
            String type,
            String suffix,
            UUID tenant,
            String tenantCode,
            UUID actor,
            String adminUser,
            String adminEmail,
            String adminName,
            String reviewerUser,
            String reviewerEmail,
            String reviewerName,
            String trace,
            Map<String, Object> values,
            Map<String, String> queryFields) {}

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
