package one.nexus.p1;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantModuleIT {

    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000ac");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    P1Properties properties;

    @BeforeAll
    static void requirePassword() {
        String password = firstEnv("NEXUS_DB_PASSWORD", "NEXUS_DATASOURCE_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "NEXUS_DB_PASSWORD or NEXUS_DATASOURCE_PASSWORD must be provided for PostgreSQL tests");
        }
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
        return body.findValuesAsText("code");
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set(DemoPlatformContextFilter.ACTOR_HEADER, ACTOR.toString());
        headers.set(DemoPlatformContextFilter.REASON_HEADER, "m0-03-it");
        headers.set(DemoPlatformContextFilter.TRACE_HEADER, "trace-" + UUID.randomUUID());
        headers.set(DemoPlatformContextFilter.ACTOR_TENANT_HEADER, properties.getPlatformTenant().getId().toString());
        HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        ResponseEntity<String> response = http.exchange(url(path), method, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        JsonNode parsed = mapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        return mapper.createObjectNode().set("body", parsed);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
