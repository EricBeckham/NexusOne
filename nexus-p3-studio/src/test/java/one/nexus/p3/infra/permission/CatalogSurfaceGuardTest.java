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
import one.nexus.permission.surface.CatalogSurfaceCatalog;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CatalogSurfaceGuardTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        CatalogSurfaceGuard guard = guard(
                decision(CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_READ,
                        new Decision(false, "no_grant")),
                null);
        Decision decision = guard.requireLedgerRead();
        assertEquals(IsolationOnlyDecision.REASON, decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantIsDeniedLikeDesktopList() {
        bindIssued();
        CatalogSurfaceGuard guard = guard(
                decision(CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_READ,
                        new Decision(false, "no_grant")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireLedgerRead);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void explicitDenyIs403OnCatalogWrite() {
        bindIssued();
        CatalogSurfaceGuard guard = guard(
                decision(CatalogSurfaceCatalog.APPLICATION_RESOURCE, CatalogSurfaceCatalog.APPLICATION_WRITE,
                        new Decision(false, "explicit_deny")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireApplicationWrite);
        assertEquals("P2.PERM.0002", ex.errorCode().code());
        assertEquals("explicit_deny", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void tenantBoundaryIs403OnBusinessTypeRead() {
        bindIssued();
        CatalogSurfaceGuard guard = guard(
                decision(CatalogSurfaceCatalog.BUSINESS_TYPE_RESOURCE, CatalogSurfaceCatalog.BUSINESS_TYPE_READ,
                        new Decision(false, "tenant_boundary")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireBusinessTypeRead);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void issuedContextCannotEmitTenantOnlyAndGoesThroughSurfaceGuard() {
        bindIssued();
        PermissionException skip = assertThrows(PermissionException.class, IsolationOnlyDecision::allow);
        assertEquals(IsolationOnlyDecision.FORBIDDEN, skip.getMessage());
        assertEquals("P2.PERM.0003", skip.errorCode().code());
        CatalogSurfaceGuard guard = guard(
                decision(CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_READ,
                        new Decision(false, "no_grant")),
                null);
        PermissionException denied = assertThrows(PermissionException.class, guard::requireLedgerRead);
        assertEquals("no_grant", denied.getMessage());
        assertEquals("P2.PERM.0003", denied.errorCode().code());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "catalog", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static CatalogSurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        return new CatalogSurfaceGuard(module, provider(seeder));
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
