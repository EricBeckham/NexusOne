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
 * G7 withdraw evidence on the existing G18 <em>leave</em> path.
 *
 * <p>Does not rewrite expense/travel catalog (already on {@code main} via #69).
 * Does not touch {@code AuditPort}. Product G18 browser close approved leave
 * and did not click withdraw — this IT is the scripted HTTP record for that
 * missing step.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g18")
class G7LeaveWithdrawIT {
    private static final UUID TENANT = UUID.fromString("77777777-7777-4777-8777-777777777701");
    private static final String TENANT_CODE = "g7w";
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000a071";
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
    void leaveWithdrawIsIdempotentAndLeavesAuditEvidence() throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "G7 Leave Withdraw", "g7-leave-wd@example.com", "g7-leave-wd"));
        Session admin = login("g7-leave-wd", "g7-leave-wd@example.com", "G7 Leave Withdraw");
        Member reviewer = provisionMember(admin, "g7-leave-rev", "g7-leave-rev@example.com", "G7 Leave Reviewer");

        JsonNode prepared = preparePublishedLeaveForm();
        UUID formId = UUID.fromString(prepared.path("form_id").asText());
        UUID ledgerId = UUID.fromString(prepared.path("ledger_id").asText());
        String physicalTable = prepared.path("physical_table").asText();
        assertTrue(physicalTable.contains("leave"), physicalTable);

        String started = mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"form_id":"%s","process_key":"nexusSequentialApproval",
                                 "assignee_type":"PERSON","assignee_ref":"%s",
                                 "values":{"title":"年假","days":2,"applicant":"alice","reason":"g7-withdraw"}}
                                """.formatted(formId, reviewer.personId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"))
                .andExpect(jsonPath("$.ledger_code").value("leave"))
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
                .andExpect(jsonPath("$[0].id").value(todoId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + reviewer.session.token)
                        .header("X-Nexus-Context-Id", reviewer.session.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.process.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.record_id").value(recordId.toString()));

        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "select approval_status from business_data." + physicalTable + " where id = ?",
                String.class,
                recordId));
        mvc.perform(get("/api/v1/p3/ledgers/" + ledgerId + "/records/" + recordId)
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.title").value("年假"))
                .andExpect(jsonPath("$.reason").value("g7-withdraw"));

        mvc.perform(post("/api/v1/p3/path/instances/" + instanceId + "/withdraw")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("WITHDRAWN"));

        mvc.perform(get("/api/v1/todos/initiated")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(instanceId))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));

        List<ChainedAuditEvent> events = auditPort.findByTraceId(TRACE);
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
                TRACE);
        assertTrue(auditRows != null && auditRows >= 3, "audit_record rows=" + auditRows + " events=" + events);
        HashChainVerification chain = auditPort.verifyChain();
        assertTrue(chain.intact(), chain.failure());

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token)
                        .header("X-Nexus-Context-Id", admin.contextId)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        // GET /api/v1/audit/traces/{id} is a bootstrap controller. The G18 path
        // host asserts the same rows through AuditPort + platform.audit_record.
        // Live JAR script (scripts/g7/withdraw-leave-http.py) hits the HTTP route.
    }

    private JsonNode preparePublishedLeaveForm() throws Exception {
        UUID actor = UUID.fromString("77777777-7777-4777-8777-777777777702");
        String suffix = Long.toString(System.nanoTime(), 36);
        String appJson = mvc.perform(post("/api/v1/p3/applications")
                        .headers(demoHeaders(actor))
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
                        .headers(demoHeaders(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ledger_code":"leave","display_name":"请假",
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
                        .headers(demoHeaders(actor))
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
                        .headers(demoHeaders(actor)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/p3/forms/" + formId + "/publish")
                        .headers(demoHeaders(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"g7-leave-withdraw\"}"))
                .andExpect(status().isOk());
        return mapper.readTree("""
                {"form_id":"%s","physical_table":"%s","ledger_id":"%s"}
                """.formatted(formId, ledger.path("physical_table").asText(), ledger.path("id").asText()));
    }

    private static org.springframework.http.HttpHeaders demoHeaders(UUID actor) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT, TENANT.toString());
        headers.add(P3IsolationFilter.HEADER_DEMO_TENANT_CODE, TENANT_CODE);
        headers.add(P3IsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString());
        headers.add(FormIsolationFilter.HEADER_DEMO_ACTOR, actor.toString());
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a070");
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
