package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.ReportSurfaceCatalog;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReportSurfaceGuardTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        ReportSurfaceGuard guard = guard(
                decision(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION,
                        new Decision(false, "no_grant")),
                null);
        Decision decision = guard.requireQuery();
        assertEquals("tenant_only", decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantIsDeniedLikeDesktopList() {
        bindIssued();
        ReportSurfaceGuard guard = guard(
                decision(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION,
                        new Decision(false, "no_grant")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireQuery);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void explicitDenyIs403OnExport() {
        bindIssued();
        ReportSurfaceGuard guard = guard(
                decision(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION,
                        new Decision(false, "explicit_deny")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireExport);
        assertEquals("P2.PERM.0002", ex.errorCode().code());
        assertEquals("explicit_deny", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void tenantBoundaryIs403OnQuery() {
        bindIssued();
        ReportSurfaceGuard guard = guard(
                decision(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION,
                        new Decision(false, "tenant_boundary")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireQuery);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void decideExportDoesNotThrowOnNoGrant() {
        bindIssued();
        ReportSurfaceGuard guard = guard(
                decision(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION,
                        new Decision(false, "no_grant")),
                null);
        Decision decision = guard.decideExport();
        assertEquals("no_grant", decision.reason());
        assertEquals(false, decision.allowed());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "sub", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static ReportSurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        return new ReportSurfaceGuard(module, provider(seeder));
    }

    private static PermissionService decision(String resource, String action, Decision decision) {
        PermissionService service = mock(PermissionService.class);
        when(service.decide(resource, action)).thenReturn(decision);
        return service;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantMemberGrantSeeder> provider(TenantMemberGrantSeeder seeder) {
        ObjectProvider<TenantMemberGrantSeeder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(seeder);
        return provider;
    }
}
