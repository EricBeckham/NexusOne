package one.nexus.p3.studio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p3.studio.api.FormIsolationFilter;
import one.nexus.p3.studio.form.FormPublishTestStubs;
import one.nexus.p3.studio.form.PublishHook;
import one.nexus.p3.studio.form.SchemaServicePublishHook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP evidence for G5 form-publish failed paths. Mid-publish leftover beyond
 * evolve DDL abort: {@link SchemaServicePublishHook} finishes
 * {@code ensurePhysicalTable}, then aborts so Running v1 + snapshot stay put.
 * Zonky CREATE/evolve abort stays on {@code JdbcFormPublishRollbackIT}.
 */
@SpringBootTest(classes = FormPublishFailureApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "nexus.studio.demo-context=true",
        "nexus.studio.persistence=memory",
        "nexus.studio.seed-demo-metadata=true",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.flowable.spring.boot.ProcessEngineAutoConfiguration,"
                + "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration,"
                + "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration,"
                + "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration,"
                + "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.app.AppEngineAutoConfiguration,"
                + "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.RestApiAutoConfiguration,"
                + "org.flowable.spring.boot.FlowableSecurityAutoConfiguration,"
                + "org.flowable.spring.boot.FlowableJpaAutoConfiguration,"
                + "org.flowable.spring.boot.EndpointAutoConfiguration,"
                + "org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration"
})
class FormPublishFailureApiTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void validateFailureKeepsDraft() throws Exception {
        String id = create("bad_leave", "坏请假单", missingLedgerBody());

        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P3.FORM.0005"));

        mvc.perform(get("/api/v1/p3/forms/" + id)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"))
                .andExpect(jsonPath("$.running_version").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/v1/p3/forms/" + id + "/versions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publishFailureLeavesRunningAndSnapshotUnchanged() throws Exception {
        String id = create("leave_form", "请假单", leaveBody());

        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("VALIDATED"));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/publish")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running_version").value(1));

        MvcResult running = mvc.perform(get("/api/v1/p3/forms/" + id + "/running")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_no").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.snapshot.version_no").value(1))
                .andReturn();
        String snapshotBefore = mapper.writeValueAsString(
                mapper.readTree(running.getResponse().getContentAsString()).get("snapshot"));

        mvc.perform(put("/api/v1/p3/forms/" + id)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_code\":\"demo\",\"form_code\":\"leave_form\",\"name\":\"请假单 v2\","
                                + leaveBody()
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("VALIDATED"));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/publish")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"v2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P3.FORM.0006"))
                .andExpect(jsonPath("$.details.cause").value(org.hamcrest.Matchers.containsString(
                        "mid-publish abort after SchemaService")));

        mvc.perform(get("/api/v1/p3/forms/" + id)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("VALIDATED"))
                .andExpect(jsonPath("$.running_version").value(1));

        MvcResult after = mvc.perform(get("/api/v1/p3/forms/" + id + "/running")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_no").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.snapshot.version_no").value(1))
                .andExpect(jsonPath("$.snapshot.name").value("请假单"))
                .andReturn();
        String snapshotAfter = mapper.writeValueAsString(
                mapper.readTree(after.getResponse().getContentAsString()).get("snapshot"));
        org.junit.jupiter.api.Assertions.assertEquals(snapshotBefore, snapshotAfter);

        mvc.perform(get("/api/v1/p3/forms/" + id + "/versions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/v1/p3/forms/" + id + "/publish-log")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.outcome == 'ROLLED_BACK')]").exists());
    }

    private String create(String formCode, String name, String bindingsJson) throws Exception {
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_code\":\"demo\",\"form_code\":\""
                                + formCode
                                + "\",\"name\":\""
                                + name
                                + "\","
                                + bindingsJson
                                + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"));
        MvcResult listed = mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode forms = mapper.readTree(listed.getResponse().getContentAsString());
        for (JsonNode node : forms) {
            if (formCode.equals(node.get("form_code").asText())) {
                return node.get("form_id").asText();
            }
        }
        throw new AssertionError("created form not listed: " + formCode);
    }

    private static String leaveBody() {
        return """
                "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true},\
                {"widget_code":"days","ledger_code":"leave_request","field_code":"days","required":true,"widget_type":"NUMBER"},\
                {"widget_code":"applicant","ledger_code":"leave_request","field_code":"applicant","required":true,"widget_type":"PERSON"}]
                """;
    }

    private static String missingLedgerBody() {
        return """
                "bindings":[{"widget_code":"title","ledger_code":"missing_ledger","field_code":"title","required":true}]
                """;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "one.nexus.p3.studio",
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
            })
    static class TestApp {
        @Bean
        @Primary
        PublishHook failSecondAfterSchemaServiceWork() {
            UUID tenant = UUID.fromString(TENANT);
            return new SchemaServicePublishHook(
                    FormPublishTestStubs.abortAfterNthEnsure(FormPublishTestStubs.abortOnNthConfirm(99), 2),
                    FormPublishTestStubs.leaveLedgers(tenant),
                    FormPublishTestStubs.leaveFields(tenant));
        }
    }
}
