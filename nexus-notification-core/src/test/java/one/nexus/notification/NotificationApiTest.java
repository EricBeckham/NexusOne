package one.nexus.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import one.nexus.notification.api.NotifyIsolationFilter;
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

@SpringBootTest(classes = NotificationApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "nexus.notify.demo-context=true",
        "nexus.notify.persistence=memory",
        "nexus.notify.outbox=record",
        "nexus.cache.mode=memory",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class NotificationApiTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    @Autowired
    MockMvc mvc;

    @Test
    void enqueueListAndRejectEmail() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .header(NotifyIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(NotifyIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"template_code":"approval.task.assigned",
                                 "recipient_subject":"user-a",
                                 "variables":{"taskTitle":"请假","actor":"王五"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.title").value("待审批：请假"));

        mvc.perform(get("/api/v1/notifications")
                        .header(NotifyIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(NotifyIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].template_code").value("approval.task.assigned"));

        mvc.perform(post("/api/v1/notifications")
                        .header(NotifyIsolationFilter.HEADER_DEMO_TENANT, TENANT)
                        .header(NotifyIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"template_code":"approval.task.assigned",
                                 "recipient_subject":"user-a",
                                 "channel":"EMAIL",
                                 "variables":{"taskTitle":"x","actor":"y"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORE.NOTIFY.0002"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {"one.nexus.notification", "one.nexus.cache"})
    static class TestApp {}
}
