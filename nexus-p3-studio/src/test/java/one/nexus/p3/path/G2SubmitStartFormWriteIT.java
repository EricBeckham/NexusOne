package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.EmbeddedFlowSupport;
import one.nexus.p3.infra.isolation.P3IsolationFilter;
import one.nexus.p3.studio.api.FormIsolationFilter;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DataScopeKind;
import org.junit.jupiter.api.AfterEach;
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
 * Path {@code POST /api/v1/p3/path/submit-start} with a {@code form_id} is a
 * dual-check: {@code form.write} (same cell as form fill) plus
 * {@code process.instance}/{@code start}. {@code no_grant} on form.write is
 * 403 before form_submission / physical row / process start. Product G18
 * leave walk stays closed; {@code tenant_member} seed cells still pass.
 */
@SpringBootTest(classes = G18PathApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("g18")
class G2SubmitStartFormWriteIT {
    private static EmbeddedPostgres postgres;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IdentityModule identity;
    @Autowired PermissionModule permission;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedFlowSupport.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedFlowSupport.register(registry, postgres);
        registry.add("nexus.permission.seed-tenant-member-grants", () -> "false");
    }

    @AfterEach
    void clearHolders() {
        TenantContextHolder.clear();
        one.nexus.isolation.context.AccessContextHolder.clear();
        one.nexus.p3.infra.isolation.P3RequestContext.clear();
    }

    @Test
    void missingFormWriteIsDeniedBeforeStart() throws Exception {
        UUID tenant = UUID.fromString("0a2a0001-0000-4000-8000-00000000a201");
        Fixture fixture = provision(tenant, "g2d", "g2-ss-deny@example.com", "g2-ss-deny");
        grant(fixture, "g2_start_only", "Start only", new String[][] {
                {"process.instance", "start"}
        });

        mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + fixture.admin.token)
                        .header("X-Nexus-Context-Id", fixture.admin.contextId)
                        .header("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(fixture)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));

        assertNoStart(fixture);
    }

    @Test
    void tenantMemberSeedStillSubmitsLeave() throws Exception {
        UUID tenant = UUID.fromString("0a2a0002-0000-4000-8000-00000000a202");
        Fixture fixture = provision(tenant, "g2s", "g2-ss-seed@example.com", "g2-ss-seed");
        identity.sessions().loadContext(UUID.fromString(fixture.admin.contextId), "g2-ss-seed");
        try {
            new TenantMemberGrantSeeder(permission).ensure();
        } finally {
            TenantContextHolder.clear();
        }

        mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + fixture.admin.token)
                        .header("X-Nexus-Context-Id", fixture.admin.contextId)
                        .header("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a202")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.process.status").value("RUNNING"));

        assertEquals(1, countSubmissions(fixture.formId));
        assertEquals(1, countProcesses(tenant));
        assertEquals(1, countPhysical(fixture.physicalTable, tenant));
    }

    @Test
    void explicitDenyFormWriteIsDeniedBeforeStart() throws Exception {
        UUID tenant = UUID.fromString("0a2a0003-0000-4000-8000-00000000a203");
        Fixture fixture = provision(tenant, "g2x", "g2-ss-xdeny@example.com", "g2-ss-xdeny");
        grant(fixture, "g2_start_and_fill", "Start and fill then deny", new String[][] {
                {"process.instance", "start"},
                {"form", "write"}
        });
        identity.sessions().loadContext(UUID.fromString(fixture.admin.contextId), "g2-ss-xdeny");
        try {
            permission.admin().grant(
                    "DENY",
                    "role",
                    "g2_start_and_fill",
                    "form",
                    "write",
                    DataScopeKind.ALL,
                    List.of(),
                    Map.of());
        } finally {
            TenantContextHolder.clear();
        }

        mvc.perform(post("/api/v1/p3/path/submit-start")
                        .header("Authorization", "Bearer " + fixture.admin.token)
                        .header("X-Nexus-Context-Id", fixture.admin.contextId)
                        .header("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a203")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(fixture)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0002"))
                .andExpect(jsonPath("$.message").value("explicit_deny"));

        assertNoStart(fixture);
    }

    private Fixture provision(UUID tenant, String tenantCode, String email, String username) throws Exception {
        identity.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G2 SubmitStart", email, username));
        Session admin = login(username, email, "G2 SubmitStart");
        Member reviewer = provisionMember(admin, username + "-rev", "rev-" + email, "G2 Reviewer");
        UUID actor = personId(tenant, email);
        JsonNode prepared = preparePublishedForm(tenant, tenantCode, actor);
        return new Fixture(
                tenant,
                admin,
                reviewer,
                UUID.fromString(prepared.path("form_id").asText()),
                prepared.path("physical_table").asText(),
                username);
    }

    private void grant(Fixture fixture, String roleCode, String roleName, String[][] cells) {
        identity.sessions().loadContext(UUID.fromString(fixture.admin.contextId), fixture.username);
        try {
            var role = permission.admin().createRole(roleCode, roleName, false);
            permission.admin().assign("user", personId(fixture.tenant, fixture.username + "@example.com").toString(),
                    role.id());
            for (String[] cell : cells) {
                permission.admin().grant(
                        "GRANT",
                        "role",
                        roleCode,
                        cell[0],
                        cell[1],
                        DataScopeKind.ALL,
                        List.of(),
                        Map.of());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void assertNoStart(Fixture fixture) {
        assertEquals(0, countSubmissions(fixture.formId));
        assertEquals(0, countProcesses(fixture.tenant));
        assertEquals(0, countPhysical(fixture.physicalTable, fixture.tenant));
    }

    private Integer countSubmissions(UUID formId) {
        return jdbc.queryForObject(
                "select count(*) from business_meta.form_submission where form_id = ?",
                Integer.class,
                formId);
    }

    private Integer countProcesses(UUID tenant) {
        return jdbc.queryForObject(
                "select count(*) from business_meta.process_instance where tenant_id = ?",
                Integer.class,
                tenant);
    }

    private Integer countPhysical(String physicalTable, UUID tenant) {
        return jdbc.queryForObject(
                "select count(*) from business_data." + physicalTable + " where tenant_id = ?",
                Integer.class,
                tenant.toString());
    }

    private static String submitBody(Fixture fixture) {
        return """
                {"form_id":"%s","process_key":"nexusSequentialApproval",
                 "assignee_type":"PERSON","assignee_ref":"%s",
                 "values":{"title":"年假","days":3,"applicant":"alice","reason":"g2-ss"}}
                """.formatted(fixture.formId, fixture.reviewer.personId);
    }

    private UUID personId(UUID tenant, String email) {
        return identity.repository().findPersonByAccount(
                        tenant, identity.repository().findAccountByEmail(email).orElseThrow().id())
                .orElseThrow()
                .id();
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
                        .content("{\"notes\":\"g2-ss\"}"))
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
        headers.add("X-Trace-Id", "0199a1c0-0000-7000-8000-00000000a200");
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

    private record Fixture(
            UUID tenant,
            Session admin,
            Member reviewer,
            UUID formId,
            String physicalTable,
            String username) {}

    record Session(String token, String contextId) {}

    record Member(Session session, UUID personId) {}
}
