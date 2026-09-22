package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * G7 leftover: expense + travel <em>query/list</em> surfaces. Withdraw HTTP
 * leftovers stay on {@code main} ({@link G7ExpenseTravelWithdrawIT}). Catalog /
 * provisioner stay on {@code main} (#69). Does not rewrite leave ITs or
 * {@code AuditPort}. Does not claim a three-type product close or reopen G18.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g7")
class G7ExpenseTravelQueryIT {
    private static final UUID EXPENSE_TENANT = UUID.fromString("77777777-7777-4777-8777-777777777731");
    private static final UUID TRAVEL_TENANT = UUID.fromString("77777777-7777-4777-8777-777777777741");
    private static final String EXPENSE_TRACE = "0199a1c0-0000-7000-8000-00000000a091";
    private static final String TRAVEL_TRACE = "0199a1c0-0000-7000-8000-00000000a092";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityModule identity;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedFlowSupport.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedFlowSupport.register(registry, postgres);
    }

    @Test
    void expenseListDetailAndTodoQuerySurfaces() throws Exception {
        queryCatalogType(new Case(
                "expense",
                "g7eq",
                EXPENSE_TENANT,
                "g7eq",
                UUID.fromString("77777777-7777-4777-8777-777777777732"),
                "g7-exp-q",
                "g7-exp-q@example.com",
                "G7 Expense Query",
                "g7-exp-qrev",
                "g7-exp-qrev@example.com",
                "G7 Expense Query Reviewer",
                EXPENSE_TRACE,
                Map.of(
                        "title", "交通费",
                        "amount", 128.50,
                        "category", "交通",
                        "payee", "张三",
                        "remark", "g7-expense-query"),
                Map.of(
                        "title", "餐费",
                        "amount", 46.00,
                        "category", "餐饮",
                        "payee", "李四",
                        "remark", "g7-expense-other"),
                Map.of("title", "交通费", "category", "交通", "payee", "张三", "remark", "g7-expense-query"),
                "category",
                "交通",
                "nosuch",
                Handle.APPROVE,
                "APPROVED"));
    }

    @Test
    void travelListDetailAndTodoQuerySurfaces() throws Exception {
        queryCatalogType(new Case(
                "travel",
                "g7tq",
                TRAVEL_TENANT,
                "g7tq",
                UUID.fromString("77777777-7777-4777-8777-777777777742"),
                "g7-trv-q",
                "g7-trv-q@example.com",
                "G7 Travel Query",
                "g7-trv-qrev",
                "g7-trv-qrev@example.com",
                "G7 Travel Query Reviewer",
                TRAVEL_TRACE,
                Map.of(
                        "title", "客户拜访",
                        "destination", "上海",
                        "days", 3,
                        "purpose", "g7-travel-query",
                        "applicant", "alice"),
                Map.of(
                        "title", "内部培训",
                        "destination", "杭州",
                        "days", 1,
                        "purpose", "g7-travel-other",
                        "applicant", "bob"),
                Map.of("title", "客户拜访", "destination", "上海", "purpose", "g7-travel-query"),
                "destination",
                "上海",
                "nosuch",
                Handle.REJECT,
                "REJECTED"));
    }

    private void queryCatalogType(Case spec) throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                spec.tenant(), spec.adminName(), spec.adminEmail(), spec.adminUser()));
        Session admin = login(spec.adminUser(), spec.adminEmail(), spec.adminName());
        Member reviewer = provisionMember(admin, spec.reviewerUser(), spec.reviewerEmail(), spec.reviewerName());

        JsonNode prepared = provision(spec);
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        UUID ledgerId = UUID.fromString(prepared.path("ledger_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        assertTrue(physicalTable.contains(spec.type()), physicalTable);

        JsonNode ledgers = read(get("/api/v1/p3/ledgers")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId));
        assertEquals(ledgerId.toString(), itemById(ledgers, ledgerId).path("id").asText(), ledgers.toString());
        assertTrue(
                itemById(ledgers, ledgerId).path("ledger_code").asText().contains(spec.type()),
                ledgers.toString());
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ledgerId.toString()))
                .andExpect(jsonPath("$.physical_table").value(physicalTable));

        JsonNode primary = submitStart(admin, spec.trace(), formId, reviewer.personId, spec.primary());
        JsonNode other = submitStart(admin, spec.trace(), formId, reviewer.personId, spec.distractor());
        UUID recordId = UUID.fromString(primary.path("record_id").asText());
        UUID otherId = UUID.fromString(other.path("record_id").asText());
        String instanceId = primary.path("process").path("id").asText();
        String todoId = primary.path("todo").path("id").asText();
        String otherTodoId = other.path("todo").path("id").asText();
        assertTrue(primary.path("ledger_code").asText().contains(spec.type()), primary.toString());

        JsonNode listed = read(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId)
                .header("X-Trace-Id", spec.trace()));
        assertEquals(2, listed.path("total").asInt(), listed.toString());
        JsonNode listedPrimary = itemById(listed.path("items"), recordId);
        assertEquals("IN_APPROVAL", listedPrimary.path("approval_status").asText(), listed.toString());
        assertQueryFields(listedPrimary, spec.queryFields());
        assertEquals(otherId.toString(), itemById(listed.path("items"), otherId).path("id").asText());

        MockHttpServletRequestBuilder filtered = get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId)
                .header("X-Trace-Id", spec.trace())
                .param(spec.filterField(), spec.filterValue());
        for (Map.Entry<String, String> field : spec.queryFields().entrySet()) {
            filtered.param(field.getKey(), field.getValue());
        }
        JsonNode queried = read(filtered);
        assertEquals(1, queried.path("total").asInt(), queried.toString());
        assertEquals(recordId.toString(), queried.path("items").get(0).path("id").asText());
        assertQueryFields(queried.path("items").get(0), spec.queryFields());

        JsonNode missed = read(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId)
                .param(spec.filterField(), spec.missValue()));
        assertEquals(0, missed.path("total").asInt(), missed.toString());
        assertTrue(missed.path("items").isEmpty() || missed.path("items").isMissingNode(), missed.toString());

        var detail = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"));
        for (Map.Entry<String, String> field : spec.queryFields().entrySet()) {
            detail.andExpect(jsonPath("$." + field.getKey()).value(field.getValue()));
        }

        JsonNode pending = read(get("/api/v1/todos/pending")
                .header("Authorization", "Bearer " + reviewer.session.token)
                .header("X-Nexus-Context-Id", reviewer.session.contextId));
        assertEquals("PENDING", itemById(pending, UUID.fromString(todoId)).path("status").asText(), pending.toString());
        assertEquals("PENDING", itemById(pending, UUID.fromString(otherTodoId)).path("status").asText(), pending.toString());
        JsonNode alias = read(get("/api/v1/p3/todos")
                .header("Authorization", "Bearer " + reviewer.session.token)
                .header("X-Nexus-Context-Id", reviewer.session.contextId));
        assertEquals(instanceId, itemById(alias, UUID.fromString(todoId)).path("instance_id").asText(), alias.toString());
        mvc.perform(get("/api/v1/todos/" + todoId)
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todoId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.instance_id").value(instanceId))
                .andExpect(jsonPath("$.assignee_person_id").value(reviewer.personId.toString()));
        JsonNode initiated = read(get("/api/v1/todos/initiated")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId));
        assertEquals("RUNNING", itemById(initiated, UUID.fromString(instanceId)).path("status").asText(), initiated.toString());

        String handlePath = spec.handle() == Handle.APPROVE
                ? "/api/v1/p3/path/tasks/" + todoId + "/approve"
                : "/api/v1/p3/path/tasks/" + todoId + "/reject";
        mvc.perform(post(handlePath)
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId)
                        .header("X-Trace-Id", spec.trace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value(spec.expectedStatus()));

        JsonNode listedAfter = read(get("/api/v1/p3/ledgers/" + ledgerId + "/records")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId));
        assertEquals(2, listedAfter.path("total").asInt(), listedAfter.toString());
        JsonNode afterPrimary = itemById(listedAfter.path("items"), recordId);
        assertEquals(spec.expectedStatus(), afterPrimary.path("approval_status").asText(), listedAfter.toString());
        assertQueryFields(afterPrimary, spec.queryFields());
        assertEquals("IN_APPROVAL", itemById(listedAfter.path("items"), otherId).path("approval_status").asText());

        var afterDetail = mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value(spec.expectedStatus()));
        for (Map.Entry<String, String> field : spec.queryFields().entrySet()) {
            afterDetail.andExpect(jsonPath("$." + field.getKey()).value(field.getValue()));
        }

        JsonNode done = read(get("/api/v1/todos/done")
                .header("Authorization", "Bearer " + reviewer.session.token)
                .header("X-Nexus-Context-Id", reviewer.session.contextId));
        assertEquals("DONE", itemById(done, UUID.fromString(todoId)).path("status").asText(), done.toString());
        mvc.perform(get("/api/v1/todos/" + todoId)
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.instance_id").value(instanceId));
        JsonNode stillPending = read(get("/api/v1/todos/pending")
                .header("Authorization", "Bearer " + reviewer.session.token)
                .header("X-Nexus-Context-Id", reviewer.session.contextId));
        assertEquals("PENDING", itemById(stillPending, UUID.fromString(otherTodoId)).path("status").asText(),
                stillPending.toString());
        assertTrue(missingId(stillPending, UUID.fromString(todoId)), stillPending.toString());
        JsonNode initiatedAfter = read(get("/api/v1/todos/initiated")
                .header("Authorization", "Bearer " + admin.token)
                .header("X-Nexus-Context-Id", admin.contextId));
        assertEquals(
                spec.expectedStatus(),
                itemById(initiatedAfter, UUID.fromString(instanceId)).path("status").asText(),
                initiatedAfter.toString());
    }

    private JsonNode submitStart(
            Session admin, String trace, UUID formId, UUID assignee, Map<String, Object> values)
            throws Exception {
        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", trace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(formId, assignee, values)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(started);
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

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(body);
    }

    private static JsonNode itemById(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("missing id " + id + " in " + items);
    }

    private static boolean missingId(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.path("id").asText())) {
                return false;
            }
        }
        return true;
    }

    private static void assertQueryFields(JsonNode row, Map<String, String> fields) {
        for (Map.Entry<String, String> field : fields.entrySet()) {
            assertEquals(field.getValue(), row.path(field.getKey()).asText(), row.toString());
        }
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
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a090");
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

    private enum Handle { APPROVE, REJECT }

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
            Map<String, Object> primary,
            Map<String, Object> distractor,
            Map<String, String> queryFields,
            String filterField,
            String filterValue,
            String missValue,
            Handle handle,
            String expectedStatus) {}

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
