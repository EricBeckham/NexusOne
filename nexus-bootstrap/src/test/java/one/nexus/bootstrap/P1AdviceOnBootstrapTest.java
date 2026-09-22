package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ensures P1 {@code DomainException} is not swallowed by bootstrap's catch-all
 * {@code Exception} advice (which would emit {@code CORE.API.0099}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(PostgresAvailableCondition.class)
class P1AdviceOnBootstrapTest {
    private static final String PLATFORM_ID = "01900000-0000-7000-8000-000000000001";
    private static final String ACTOR = "01900000-0000-7000-8000-0000000000ac";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void datasourceAndDemo(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> PostgresAvailableCondition.env("NEXUS_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexusone"));
        registry.add("spring.datasource.username",
                () -> PostgresAvailableCondition.env("NEXUS_DB_USER", "nexus"));
        registry.add("spring.datasource.password",
                () -> PostgresAvailableCondition.env("NEXUS_DB_PASSWORD", ""));
        registry.add("nexus.p1.demo-context.enabled", () -> "true");
        registry.add("nexus.p1.quota.app-limit", () -> "2");
        registry.add("nexus.p1.quota.custom-ledger-limit", () -> "2");
    }

    @Test
    void disablingPlatformTenantReturnsP1CodeNotInternalError() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://127.0.0.1:" + port + "/api/v1/p1/tenants/" + PLATFORM_ID + "/disable"))
                        .header("Content-Type", "application/json")
                        .header("X-Nexus-Demo-Actor-Id", ACTOR)
                        .header("X-Nexus-Demo-Reason", "m0-03-bootstrap-it")
                        .header("X-Nexus-Trace-Id", "trace-p1-advice")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"confirm\":true,\"reason\":\"should fail\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"P1.TENANT.0004\""), response.body());
        assertTrue(response.body().contains("reserved platform tenant cannot be disabled"), response.body());
    }
}
