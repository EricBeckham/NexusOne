package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import one.nexus.p1.domain.PlatformDuty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** AUTH6G2: the bridge allowlist is exact — listed GETs only, one UUID segment at most, never a prefix. */
class Auth6RouteTableTest {
    static final String ID = "01900000-0000-7000-8000-000000000101";

    static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/p1/tenants, tenant, list",
            "/api/v1/p1/tenant-registrations, tenant, list",
            "/api/v1/p1/tenants/tree, tenant, list",
            "/api/v1/p1/tenants/" + ID + "/children, tenant, get",
            "/api/v1/p1/tenants/" + ID + "/ancestors, tenant, get",
            "/api/v1/p1/platform/services, tenant, list",
            "/api/v1/p1/platform/form-templates, tenant, list",
            "/api/v1/p1/platform/form-templates/" + ID + ", tenant, list",
            "/api/v1/p1/tenants/" + ID + "/entitlement, tenant.entitlement, get",
            "/api/v1/p1/tenants/" + ID + "/quotas, tenant, get",
            "/api/v1/p4/product-definitions, tenant, list",
            "/api/v1/p4/product-definitions/" + ID + ", tenant, list",
            "/api/v1/p4/product-definitions/" + ID + "/config-readiness, tenant, list",
    })
    void systemAdminReadsAreBridged(String uri, String resource, String action) {
        var route = P1PlatformReadContextFilter.route(request("GET", uri)).orElseThrow();
        assertEquals(PlatformDuty.SYSTEM_ADMIN, route.duty());
        assertEquals("platform_system_admin", route.dutyRole());
        assertEquals(resource, route.resource());
        assertEquals(action, route.action());
    }

    @org.junit.jupiter.api.Test
    void auditSearchBindsAuditAdminNotSystemAdmin() {
        var route = P1PlatformReadContextFilter.route(request("GET", "/api/v1/audit/events")).orElseThrow();
        assertEquals(PlatformDuty.AUDIT_ADMIN, route.duty());
        assertEquals("platform_audit_admin", route.dutyRole());
        assertEquals("audit", route.resource());
        assertEquals("read", route.action());
        assertTrue(P1PlatformReadContextFilter.route(request("POST", "/api/v1/audit/events")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void writesAreNeverBridged(String method) {
        for (String uri : new String[] {
                "/api/v1/p1/tenants", "/api/v1/p1/tenants/" + ID + "/parent",
                "/api/v1/p1/platform/form-templates", "/api/v1/p1/platform/form-templates/" + ID,
                "/api/v1/p1/platform/form-templates/" + ID + "/validate",
                "/api/v1/p1/platform/form-templates/" + ID + "/publish",
                // AUTH6H2 writes stay outside the bridge.
                "/api/v1/p1/tenants/" + ID + "/quotas/consume", "/api/v1/p1/tenants/" + ID + "/quotas/release",
                "/api/v1/p4/product-definitions", "/api/v1/p4/product-definitions/" + ID,
                "/api/v1/p4/product-definitions/import", "/api/v1/p4/product-definitions/" + ID + "/skus"}) {
            assertTrue(P1PlatformReadContextFilter.route(request(method, uri)).isEmpty(), method + " " + uri);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/p1/tenants/" + ID,
            "/api/v1/p1/tenants/" + ID + "/parent",
            "/api/v1/p1/tenants/not-a-uuid/children",
            "/api/v1/p1/tenants/" + ID + "/children/extra",
            "/api/v1/p1/tenants/tree/extra",
            "/api/v1/p1/platform/services/x",
            "/api/v1/p1/platform/form-templates/" + ID + "/validate",
            "/api/v1/p1/platform/form-templates/not-a-uuid",
            "/api/v1/p1/platform/tenants",
            "/api/v1/audit/events/x",
            "/api/v1/audit/traces/abc",
            "/api/v1/audit/chain/verify",
            "/api/v1/p1/tenants/",
            // AUTH6H2 boundary: only the listed product-definition reads, no export / samples / print / import jobs.
            "/api/v1/p1/tenants/" + ID + "/config",
            "/api/v1/p1/tenants/not-a-uuid/quotas",
            "/api/v1/p4/product-definitions/" + ID + "/export",
            "/api/v1/p4/product-definitions/" + ID + "/samples/ledger",
            "/api/v1/p4/product-definitions/" + ID + "/print-mapping",
            "/api/v1/p4/product-definitions/import-jobs/" + ID,
            "/api/v1/p4/product-definitions/not-a-uuid",
            "/api/v1/p4/skus",
    })
    void unlistedGetsAreNotBridged(String uri) {
        assertTrue(P1PlatformReadContextFilter.route(request("GET", uri)).isEmpty(), uri);
    }
}
