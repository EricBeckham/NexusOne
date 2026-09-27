package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.infra.isolation.ThreadLocalIsolationGuard;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.DataScopeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;

/** Real IAM + permission engine fixture; no DB or externally configured services. */
class PlatformContentContextFilterTest extends Auth6RouteRealChainTest {
    PlatformContentContextFilter content() { return new PlatformContentContextFilter(iam, permissions, properties); }

    @ParameterizedTest
    @CsvSource({
        "POST,/api/v1/p1/platform/form-templates", "PUT,/api/v1/p1/platform/form-templates/" + ID,
        "POST,/api/v1/p1/platform/form-templates/" + ID + "/validate",
        "POST,/api/v1/p1/platform/form-templates/" + ID + "/publish",
        "POST,/api/v1/p4/product-definitions", "PUT,/api/v1/p4/product-definitions/" + ID,
        "PUT,/api/v1/p4/product-definitions/" + ID + "/skus/expense.standard",
        "POST,/api/v1/p4/apps", "POST,/api/v1/p4/apps/" + ID + "/versions",
        "POST,/api/v1/p4/versions/" + ID + "/review", "POST,/api/v1/p4/versions/" + ID + "/approve",
        "POST,/api/v1/p4/versions/" + ID + "/publish", "POST,/api/v1/p4/skus",
        "GET,/api/v1/p4/activation-applications", "POST,/api/v1/p4/activation-applications/" + ID + "/approve"
    })
    void realSystemAdminCanConfigureContentAndEveryRouteFailsClosed(String method, String path) throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        var filter = content();
        var response = new MockHttpServletResponse();
        boolean[] reached = {false};
        filter.doFilter(request(method, path, token, person.contextId().toString()), response, (req,res) -> {
            reached[0] = true;
            new ThreadLocalIsolationGuard().requirePlatformContext("content", PlatformDuty.SYSTEM_ADMIN);
            assertEquals(person.personId(), IsolationContextHolder.get().actor().subjectId());
            assertEquals(platform, IsolationContextHolder.get().platform().auditTenantId(platform));
        });
        assertTrue(reached[0]);
        assertNull(IsolationContextHolder.get());
        for (String context : new String[] {null, UUID.randomUUID().toString()}) {
            var denied = new MockHttpServletResponse();
            filter.doFilter(request(method, path, token, context), denied, (req,res) -> fail("unauthenticated write"));
            assertEquals(401, denied.getStatus());
            assertTrue(denied.getContentAsString().contains("PLATFORM.CONTENT_DENIED"));
        }
    }

    @Test void refusesWrongDutyRevokedSessionAndExplicitPermissionDeny() throws Exception {
        assignOnly(PlatformDutyCatalog.AUDIT_ADMIN);
        denyContent("duty_set");
        // Another fresh fixture with the correct duty but explicit deny.
        setUp(); assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        iam.sessions().loadContext(person.contextId(), iam.sessions().authenticate(token));
        permissions.admin().grant("DENY", "role", PlatformDutyCatalog.SYSTEM_ADMIN,
                "tenant.config", "update", DataScopeKind.ALL, List.of(), java.util.Map.of());
        one.nexus.p2.kernel.TenantContextHolder.clear();
        denyContent("permission_denied");
        iam.repository().revokeContext(person.contextId(), person.accountId());
        var response = new MockHttpServletResponse();
        content().doFilter(request("POST", "/api/v1/p1/platform/form-templates", token, person.contextId().toString()),
                response, (req,res) -> fail());
        assertEquals(401, response.getStatus());
    }

    private void denyContent(String reason) throws Exception {
        var response = new MockHttpServletResponse();
        content().doFilter(request("POST", "/api/v1/p1/platform/form-templates", token, person.contextId().toString()),
                response, (req,res) -> fail());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains(reason));
    }

    @Test void exactAllowlistAndMineCannotBypassWriteAuthentication() throws Exception {
        for (String path : List.of("/api/v1/p1/tenants", "/api/v1/p4/product-definitions/import",
                "/api/v1/p1/platform/form-templates/not-a-uuid/publish", "/api/v1/audit/events")) {
            assertTrue(content().shouldNotFilter(request("POST", path, null, null)));
        }
        var mine = request("GET", "/api/v1/p4/activation-applications", null, null);
        mine.addParameter("mine", "true");
        assertTrue(content().shouldNotFilter(mine));
        var attack = request("POST", "/api/v1/p4/apps", null, null);
        attack.addParameter("mine", "true");
        attack.addHeader("X-Nexus-Demo-Actor-Id", person.personId().toString());
        var response = new MockHttpServletResponse();
        content().doFilter(attack, response, (req,res) -> fail());
        assertEquals(401, response.getStatus());
    }
}
