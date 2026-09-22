package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import one.nexus.isolation.IsolationKernel;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.EmbeddedFlowSupport;
import one.nexus.p3.infra.isolation.P3IsolationFilter;
import one.nexus.p3.infra.isolation.P3IsolationSupport;
import one.nexus.p3.path.BusinessPathRecords.SubmitStartCommand;
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

@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g18")
class G18BusinessPathIT {
    private static final UUID TENANT = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String TENANT_CODE = "g18a";
    /** {@link one.nexus.common.trace.TraceIds#resolve} only accepts UUID / OTEL traces. */
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000a018";
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityModule identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditPort auditPort;
    @Autowired G18RollbackProbe rollbackProbe;
    @Autowired IsolationKernel isolationKernel;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedFlowSupport.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedFlowSupport.register(registry, postgres);
    }

    @Test
    void publishSubmitApprovalOutboxAndAuditShareOnePath() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G18 Admin", "g18-admin@example.com", "g18-admin"));
        Session admin = login("g18-admin", "g18-admin@example.com", "G18 Admin");
        Member reviewer = provisionMember(admin, "g18-reviewer", "g18-reviewer@example.com", "G18 Reviewer");

        JsonNode prepared = preparePublishedForm();
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        assertTrue(physicalTable.startsWith("l_g18a_"), physicalTable);

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"form_id":"%s","process_key":"nexusSequentialApproval",
                                 "assignee_type":"PERSON","assignee_ref":"%s",
                                 "values":{"title":"年假","days":3,"applicant":"alice","reason":"family"}}
                                """.formatted(formId, reviewer.personId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode startJson = mapper.readTree(started);
        UUID recordId = UUID.fromString(startJson.path("record_id").asText());
        String todoId = startJson.path("todo").path("id").asText();

        Integer rows = jdbc.queryForObject(
                "select count(*) from business_data." + physicalTable
                        + " where id = ? and tenant_id = ? and deleted_at is null",
                Integer.class,
                recordId,
                TENANT.toString());
        assertEquals(1, rows);
        assertEquals("IN_APPROVAL", jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));

        Integer outbox = jdbc.queryForObject(
                "select count(*) from platform.exec_outbox where destination = 'notify.inbox' and trace_id = ?",
                Integer.class,
                TRACE);
        assertEquals(1, outbox, () -> jdbc.queryForList(
                "select destination, trace_id, status, event_type from platform.exec_outbox").toString());
        assertEquals("PENDING", jdbc.queryForObject(
                "select status from platform.exec_outbox where destination = 'notify.inbox' and trace_id = ? limit 1",
                String.class,
                TRACE));

        List<ChainedAuditEvent> submitted = auditPort.findByTraceId(TRACE);
        assertTrue(submitted.stream().anyMatch(event -> "p3.path.submitted".equals(event.event().eventType())),
                submitted.toString());
        assertTrue(submitted.stream().anyMatch(event -> "p3.approval.started".equals(event.event().eventType())),
                submitted.toString());

        mvc.perform(post("/api/v1/p3/path/tasks/" + todoId + "/approve")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("APPROVED"))
                .andExpect(jsonPath("$.process.status").value("APPROVED"));

        assertEquals("APPROVED", jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from platform.exec_outbox where destination = 'notify.inbox' and trace_id = ?",
                Integer.class,
                TRACE));
        List<ChainedAuditEvent> decided = auditPort.findByTraceId(TRACE);
        assertTrue(decided.stream().anyMatch(event -> "p3.approval.approved".equals(event.event().eventType())));

        mvc.perform(post("/api/v1/p3/path/tasks/" + todoId + "/approve")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("APPROVED"));
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from platform.exec_outbox where destination = 'notify.inbox' and trace_id = ?",
                Integer.class,
                TRACE));

        mvc.perform(get("/api/v1/todos/done")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(todoId));
    }

    @Test
    void approvalAndLedgerRowRollBackTogether() throws Exception {
        UUID tenant = UUID.fromString("66666666-6666-4666-8666-666666666666");
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G18 Rollback", "g18-rollback@example.com", "g18-rollback"));
        Session admin = login("g18-rollback", "g18-rollback@example.com", "G18 Rollback");
        var person = identity.repository().findPersonByAccount(
                tenant, identity.repository().findAccountByEmail("g18-rollback@example.com").orElseThrow().id())
                .orElseThrow();
        JsonNode prepared = preparePublishedForm(tenant, "g18b", person.id());
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        String businessTrace = "0199a1c0-0000-7000-8000-00000000a019";

        P3IsolationSupport.bind(isolationKernel, tenant, "g18b", person.id().toString(), businessTrace);
        identity.sessions().loadContext(UUID.fromString(admin.contextId), "g18-rollback");
        try {
            assertThrows(IllegalStateException.class, () -> rollbackProbe.submitStartThenFail(new SubmitStartCommand(
                    formId,
                    java.util.Map.of("title", "rollback", "days", 1, "applicant", "bob"),
                    "nexusSequentialApproval",
                    AssigneeType.PERSON,
                    person.id().toString())));
        } finally {
            clearHolders();
        }

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_meta.form_submission where form_id = ?",
                Integer.class,
                formId));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_meta.process_instance where tenant_id = ?",
                Integer.class,
                tenant));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from business_data." + physicalTable + " where tenant_id = ?",
                Integer.class,
                tenant.toString()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from platform.exec_outbox where trace_id = ?",
                Integer.class,
                businessTrace));
    }

    private JsonNode preparePublishedForm() throws Exception {
        return preparePublishedForm(TENANT, TENANT_CODE, UUID.fromString("55555555-5555-4555-8555-555555555501"));
    }

    private JsonNode preparePublishedForm(UUID tenant, String tenantCode, UUID actor) throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String appJson = mvc.perform(post("/api/v1/p3/applications")
                        .headers(demoHeaders(tenant, tenantCode, actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"hr%s","display_name":"HR","kind":"CUSTOM"}
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
                                {"ledger_code":"leave","display_name":"Leave",
                                 "fields":[
                                   {"field_code":"title","display_name":"Title","field_type":"TEXT","required":true},
                                   {"field_code":"days","display_name":"Days","field_type":"INTEGER","required":true},
                                   {"field_code":"applicant","display_name":"Applicant","field_type":"TEXT","required":true},
                                   {"field_code":"reason","display_name":"Reason","field_type":"TEXT","required":false},
                                   {"field_code":"approval_status","display_name":"Status","field_type":"TEXT","required":false}
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
                                {"app_code":"hr%s","form_code":"leave_form%s","name":"请假单",
                                 "bindings":[
                                   {"widget_code":"title","ledger_code":"leave","field_code":"title","required":true},
                                   {"widget_code":"days","ledger_code":"leave","field_code":"days","required":true,"widget_type":"NUMBER"},
                                   {"widget_code":"applicant","ledger_code":"leave","field_code":"applicant","required":true,"widget_type":"PERSON"},
                                   {"widget_code":"reason","ledger_code":"leave","field_code":"reason","widget_type":"TEXTAREA"}
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
                        .content("{\"notes\":\"g18\"}"))
                .andExpect(status().isOk());
        return mapper.readTree("""
                {"form_id":"%s","physical_table":"%s","ledger_id":"%s"}
                """.formatted(formId, ledger.path("physical_table").asText(), ledger.path("id").asText()));
    }

    private static org.springframework.http.HttpHeaders demoHeaders(UUID tenant, String code, UUID actor) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, code);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_TENANT, tenant.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a017");
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

    private static void clearHolders() {
        one.nexus.p2.kernel.TenantContextHolder.clear();
        one.nexus.isolation.context.AccessContextHolder.clear();
        one.nexus.p3.infra.isolation.P3RequestContext.clear();
    }

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
