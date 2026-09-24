package one.nexus.p3.studio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import one.nexus.p3.studio.api.FormIsolationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FormApiTest.TestApp.class)
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
class FormApiTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void createValidatePublishFillAndRejectPhysicalBinding() throws Exception {
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"leave_form","name":"请假单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true},
                                             {"widget_code":"days","ledger_code":"leave_request","field_code":"days","required":true,"widget_type":"NUMBER"},
                                             {"widget_code":"applicant","ledger_code":"leave_request","field_code":"applicant","required":true,"widget_type":"PERSON"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"));

        MvcResult listed = mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode forms = mapper.readTree(listed.getResponse().getContentAsString());
        String id = forms.get(0).get("form_id").asText();

        mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_B)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("VALIDATED"));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/publish")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running_version").value(1));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/submissions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"title\":\"年假\",\"days\":2,\"applicant\":\"alice\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running_version").value(1));

        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"bad_form","name":"坏表单",
                                 "bindings":[{"widget_code":"x","ledger_code":"leave_request","field_code":"title","column_name":"c_title"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P3.FORM.0004"));

        mvc.perform(get("/api/v1/p3/forms"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CORE.CTX.0001"));

        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"unknown_ledger_form","name":"未知台账",
                                 "bindings":[{"widget_code":"title","ledger_code":"missing_ledger","field_code":"title","required":true}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"));

        MvcResult listedBad = mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andReturn();
        String badId = null;
        for (JsonNode node : mapper.readTree(listedBad.getResponse().getContentAsString())) {
            if ("unknown_ledger_form".equals(node.get("form_code").asText())) {
                badId = node.get("form_id").asText();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(badId);

        mvc.perform(post("/api/v1/p3/forms/" + badId + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P3.FORM.0005"));

        mvc.perform(get("/api/v1/p3/forms/" + badId)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"))
                .andExpect(jsonPath("$.running_version").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void publishCenterListsOnlyTheCallerTenant() throws Exception {
        mvc.perform(get("/api/v1/p3/publish-center"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"hr","form_code":"center_form","name":"发布中心单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true}]}
                                """))
                .andExpect(status().isCreated());
        MvcResult listed = mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andReturn();
        String id = null;
        for (JsonNode node : mapper.readTree(listed.getResponse().getContentAsString())) {
            if ("center_form".equals(node.get("form_code").asText())) {
                id = node.get("form_id").asText();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(id);

        mvc.perform(get("/api/v1/p3/publish-center")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.form_id == '" + id + "')].status")
                        .value(org.hamcrest.Matchers.hasItem("Draft")));

        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/p3/forms/" + id + "/publish")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"v1\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/p3/publish-center")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.form_id == '" + id + "')].status")
                        .value(org.hamcrest.Matchers.hasItem("Running")));
        mvc.perform(get("/api/v1/p3/publish-center/" + id)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_A)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_execution").value(false))
                .andExpect(jsonPath("$.schema_notice").value("回滚不会回滚共享物理 Schema，也不会删除已有业务数据。"))
                .andExpect(jsonPath("$.logs").isNotEmpty());
        mvc.perform(get("/api/v1/p3/publish-center")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_B)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.form_id == '" + id + "')]").isEmpty());
        mvc.perform(get("/api/v1/p3/publish-center/" + id)
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT_B)
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-b"))
                .andExpect(status().isNotFound());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "one.nexus.p3.studio",
            excludeFilters = @ComponentScan.Filter(
                    type = org.springframework.context.annotation.FilterType.ANNOTATION,
                    classes = SpringBootConfiguration.class))
    static class TestApp {}
}
