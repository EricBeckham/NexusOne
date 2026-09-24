package one.nexus.p5.connect;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.p5.connect.ConnectModels.ApiCallLog;
import one.nexus.p5.connect.ConnectModels.ApiClientView;
import one.nexus.p5.connect.ConnectModels.DeliveryStatus;
import one.nexus.p5.connect.ConnectModels.WebhookDeliveryView;
import one.nexus.p5.connect.ConnectModels.WebhookSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConnectReadApiTest {
    private static final UUID CLIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000031");
    private static final UUID WEBHOOK_ID = UUID.fromString("01900000-0000-7000-8000-000000000032");
    private static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000033");
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    private ConnectService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ConnectService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ConnectController(service)).build();
        when(service.clients()).thenReturn(List.of(
                new ApiClientView(CLIENT_ID, TENANT_ID, "scheduler", "nx_prefix",
                        List.of("integration:call"), 60, true, NOW)));
        when(service.client(CLIENT_ID)).thenReturn(
                new ApiClientView(CLIENT_ID, TENANT_ID, "scheduler", "nx_prefix",
                        List.of("integration:call"), 60, true, NOW));
        when(service.subscriptions()).thenReturn(List.of(
                new WebhookSubscription(WEBHOOK_ID, TENANT_ID, "sink", "https://hooks.example.test",
                        List.of("record.changed"), true, NOW)));
        when(service.subscription(WEBHOOK_ID)).thenReturn(
                new WebhookSubscription(WEBHOOK_ID, TENANT_ID, "sink", "https://hooks.example.test",
                        List.of("record.changed"), true, NOW));
        when(service.deliveries(WEBHOOK_ID)).thenReturn(List.of(
                new WebhookDeliveryView(UUID.randomUUID(), WEBHOOK_ID, "event-1", "record.changed",
                        DeliveryStatus.DELIVERED, 1, NOW, NOW, NOW)));
        when(service.callLogs()).thenReturn(List.of(
                new ApiCallLog(UUID.randomUUID(), TENANT_ID, CLIENT_ID, "integration:call",
                        "/internal/call", "POST", "SUCCEEDED", "trace-1", NOW)));
    }

    @Test
    void servesClientWebhookDeliveryAndCallLogReadModelsWithoutSecretsOrBodies() throws Exception {
        mvc.perform(get("/api/v1/p5/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("scheduler"))
                .andExpect(jsonPath("$[0].secretDigest").doesNotExist());
        mvc.perform(get("/api/v1/p5/clients/{id}", CLIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix").value("nx_prefix"))
                .andExpect(jsonPath("$.secretDigest").doesNotExist());
        mvc.perform(get("/api/v1/p5/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endpoint").value("https://hooks.example.test"));
        mvc.perform(get("/api/v1/p5/webhooks/{id}", WEBHOOK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTypes[0]").value("record.changed"));
        mvc.perform(get("/api/v1/p5/webhooks/{id}/deliveries", WEBHOOK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[0].payload").doesNotExist());
        mvc.perform(get("/api/v1/p5/call-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiPath").value("/internal/call"))
                .andExpect(jsonPath("$[0].response").doesNotExist());
    }
}
