package one.nexus.p4.market;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p4.market.MarketModels.AppPackage;
import one.nexus.p4.market.MarketModels.AppVersion;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import one.nexus.p4.market.MarketModels.SubscriptionStatus;
import one.nexus.p4.market.MarketModels.VersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketReadApiTest {
    private static final UUID APP_ID = UUID.fromString("01900000-0000-7000-8000-000000000021");
    private static final UUID VERSION_ID = UUID.fromString("01900000-0000-7000-8000-000000000022");
    private static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000023");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("01900000-0000-7000-8000-000000000024");
    private static final UUID INSTALLATION_ID = UUID.fromString("01900000-0000-7000-8000-000000000025");
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    private MarketService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(MarketService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MarketController(service)).build();
        AppPackage app = new AppPackage(APP_ID, "crm", "CRM", "Customer management", NOW);
        AppVersion version = new AppVersion(VERSION_ID, APP_ID, "1.0.0", VersionStatus.PUBLISHED, null, NOW, NOW);
        Sku sku = new Sku("CRM_BASIC", APP_ID, "Basic", Map.of());
        when(service.apps()).thenReturn(List.of(app));
        when(service.getApp(APP_ID)).thenReturn(app);
        when(service.versions(APP_ID)).thenReturn(List.of(version));
        when(service.getVersion(VERSION_ID)).thenReturn(version);
        when(service.skus()).thenReturn(List.of(sku));
        when(service.getSku("CRM_BASIC")).thenReturn(sku);
        Subscription subscription = new Subscription(
                SUBSCRIPTION_ID, TENANT_ID, "CRM_BASIC", SubscriptionStatus.ACTIVE, NOW, null);
        Installation installation = new Installation(
                INSTALLATION_ID, TENANT_ID, APP_ID, VERSION_ID, InstallationStatus.INSTALLED, NOW, NOW);
        when(service.subscriptions()).thenReturn(List.of(subscription));
        when(service.getSubscription(SUBSCRIPTION_ID)).thenReturn(subscription);
        when(service.installations()).thenReturn(List.of(installation));
        when(service.getInstallation(INSTALLATION_ID)).thenReturn(installation);
    }

    @Test
    void servesPackageVersionSkuSubscriptionAndInstallationReadModels() throws Exception {
        mvc.perform(get("/api/v1/p4/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("crm"));
        mvc.perform(get("/api/v1/p4/apps/{id}", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(APP_ID.toString()));
        mvc.perform(get("/api/v1/p4/apps/{id}/versions", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value("1.0.0"));
        mvc.perform(get("/api/v1/p4/versions/{id}", VERSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/p4/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CRM_BASIC"));
        mvc.perform(get("/api/v1/p4/skus/CRM_BASIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Basic"));
        mvc.perform(get("/api/v1/p4/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_ID.toString()));
        mvc.perform(get("/api/v1/p4/subscriptions/{id}", SUBSCRIPTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("CRM_BASIC"));
        mvc.perform(get("/api/v1/p4/installations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("INSTALLED"));
        mvc.perform(get("/api/v1/p4/installations/{id}", INSTALLATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID.toString()));
    }
}
