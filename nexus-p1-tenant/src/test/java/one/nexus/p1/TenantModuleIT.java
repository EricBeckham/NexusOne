package one.nexus.p1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;

@SpringBootTest(classes = P1TenantTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantModuleIT {

    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000ac");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    P1Properties properties;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void requirePassword() {
        String password = firstEnv("NEXUS_DB_PASSWORD", "NEXUS_DATASOURCE_PASSWORD");
        assumeTrue(
                password != null && !password.isBlank(),
                "SKIP live PostgreSQL: NEXUS_DB_PASSWORD / NEXUS_DATASOURCE_PASSWORD not set");
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Test
    void twoTenantsCreateStopPlatformProtectedAndQuotaRejectAreAudited() throws Exception {
        JsonNode platform = get("/api/v1/p1/platform-tenant").path("body");
        assertThat(platform.path("code").asText()).isEqualTo("platform");
        assertThat(platform.path("kind").asText()).isEqualTo("PLATFORM");
        assertThat(platform.path("status").asText()).isEqualTo("ENABLED");

        String suffix = Long.toString(System.nanoTime(), 36);
        JsonNode alpha = createTenant("alpha_" + suffix, "Alpha " + suffix);
        JsonNode beta = createTenant("beta_" + suffix, "Beta " + suffix);
        UUID alphaId = UUID.fromString(alpha.path("tenant").path("id").asText());
        UUID betaId = UUID.fromString(beta.path("tenant").path("id").asText());
        assertThat(alpha.path("tenant").path("status").asText()).isEqualTo("ENABLED");
        assertThat(beta.path("admin_init").path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(alpha.path("entitlement").path("source").asText()).isEqualTo("DEFAULT_PACK");
        assertThat(alpha.path("entitlement").path("payload").path("schema").asText())
                .isEqualTo("p4.runtime-entitlement.v1");

        JsonNode stopped = exchange(
                HttpMethod.POST,
                "/api/v1/p1/tenants/" + alphaId + "/disable",
                Map.of("confirm", true, "reason", "m0-03 demo stop"),
                HttpStatus.OK).path("body");
        assertThat(stopped.path("status").asText()).isEqualTo("DISABLED");

        JsonNode enabled = exchange(
                HttpMethod.POST,
                "/api/v1/p1/tenants/" + alphaId + "/enable",
                Map.of("confirm", true),
                HttpStatus.OK).path("body");
        assertThat(enabled.path("status").asText()).isEqualTo("ENABLED");

        UUID platformId = properties.getPlatformTenant().getId();
        JsonNode rejectedStop = exchange(
                HttpMethod.POST,
                "/api/v1/p1/tenants/" + platformId + "/disable",
                Map.of("confirm", true, "reason", "should fail"),
                HttpStatus.BAD_REQUEST).path("body");
        assertThat(rejectedStop.path("code").asText()).isEqualTo("P1.TENANT.0004");
        assertThat(get("/api/v1/p1/platform-tenant").path("body").path("status").asText()).isEqualTo("ENABLED");

        JsonNode platformAudits = get(
                "/api/v1/p1/tenants/" + platformId + "/audit-events?event_type=p1.tenant.disable.rejected")
                .path("body");
        assertThat(platformAudits.isArray()).isTrue();
        assertThat(platformAudits.size()).isGreaterThan(0);
        JsonNode rejectEvent = platformAudits.get(0);
        assertThat(rejectEvent.path("event_type").asText()).isEqualTo("p1.tenant.disable.rejected");
        assertThat(rejectEvent.path("event_version").asInt()).isEqualTo(1);
        assertThat(rejectEvent.path("outcome").asText()).isEqualTo("REJECTED");
        assertThat(rejectEvent.path("platform_context").asBoolean()).isTrue();
        assertThat(rejectEvent.path("trace_id").asText()).isNotBlank();
        assertThat(rejectEvent.path("resource_type").asText()).isEqualTo("tenant");
        assertThat(rejectEvent.path("details").path("reason_code").asText()).isEqualTo("P1.TENANT.0004");

        exchange(
                HttpMethod.POST,
                "/api/v1/p1/tenants/" + betaId + "/quotas/consume",
                Map.of("metric", "CUSTOM_LEDGER", "amount", 2, "idempotency_key", "ledger-batch-1"),
                HttpStatus.OK);
        JsonNode over = exchange(
                HttpMethod.POST,
                "/api/v1/p1/tenants/" + betaId + "/quotas/consume",
                Map.of("metric", "CUSTOM_LEDGER", "amount", 1, "idempotency_key", "ledger-batch-2"),
                HttpStatus.UNPROCESSABLE_ENTITY).path("body");
        assertThat(over.path("code").asText()).isEqualTo("P1.QUOTA.0001");
        assertThat(over.path("details").path("limit").asInt()).isEqualTo(2);
        assertThat(over.path("details").path("used").asInt()).isEqualTo(2);
        assertThat(over.path("details").path("requested").asInt()).isEqualTo(1);

        JsonNode quotaAudits = get(
                "/api/v1/p1/tenants/" + betaId + "/audit-events?event_type=p1.quota.rejected")
                .path("body");
        assertThat(quotaAudits.size()).isGreaterThan(0);
        JsonNode quotaEvent = quotaAudits.get(0);
        assertThat(quotaEvent.path("event_type").asText()).isEqualTo("p1.quota.rejected");
        assertThat(quotaEvent.path("outcome").asText()).isEqualTo("REJECTED");
        assertThat(quotaEvent.path("resource_type").asText()).isEqualTo("quota");
        assertThat(quotaEvent.path("resource_id").asText()).isEqualTo("CUSTOM_LEDGER");
        assertThat(quotaEvent.path("details").path("policy").asText()).isEqualTo("REJECT");
        assertThat(quotaEvent.path("details").path("reason_code").asText()).isEqualTo("P1.QUOTA.0001");

        JsonNode stillEnabled = get("/api/v1/p1/tenants/" + betaId).path("body");
        assertThat(stillEnabled.path("status").asText()).isEqualTo("ENABLED");
        assertThat(listCodes()).contains("platform", "alpha_" + suffix, "beta_" + suffix);
    }

    private List<String> listCodes() throws Exception {
        JsonNode body = get("/api/v1/p1/tenants").path("body");
        ArrayList<String> codes = new ArrayList<>();
        if (body != null && body.isArray()) {
            for (JsonNode node : body) {
                JsonNode code = node.get("code");
                if (code != null && !code.isNull()) {
                    codes.add(code.asText());
                }
            }
        }
        return codes;
    }

    private JsonNode createTenant(String code, String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "code", code,
                "legal_name", name + " Ltd",
                "admin", Map.of("display_name", "Admin " + code, "email", code + "@example.com"),
                "config", Map.of("locale", "zh-CN", "timezone", "Asia/Shanghai", "currency", "CNY"));
        JsonNode response = exchange(HttpMethod.POST, "/api/v1/p1/tenants", body, HttpStatus.CREATED);
        return response.path("body");
    }

    private JsonNode get(String path) throws Exception {
        return exchange(HttpMethod.GET, path, null, HttpStatus.OK);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, HttpStatus expected) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR.toString())
                .header(DemoPlatformContextFilter.REASON_HEADER, "m0-03-it")
                .header(DemoPlatformContextFilter.TRACE_HEADER, "trace-" + UUID.randomUUID())
                .header(DemoPlatformContextFilter.ACTOR_TENANT_HEADER, properties.getPlatformTenant().getId().toString());
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        HttpRequest request = builder.method(method.name(), publisher).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(expected.value());
        JsonNode parsed = mapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        return mapper.createObjectNode().set("body", parsed);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
