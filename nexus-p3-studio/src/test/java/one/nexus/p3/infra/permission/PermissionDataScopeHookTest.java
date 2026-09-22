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
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PermissionDataScopeHookTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        PermissionDataScopeHook hook = hook(decision("ledger", "read", new Decision(false, "no_grant")), null);
        ScopedRead scoped = hook.bind("tenant-a", "ledger", "read");
        assertEquals("tenant_only", scoped.decision().reason());
        assertTrue(scoped.query().whereSql().contains("tenant_id"));
    }

    @Test
    void noGrantIsDeniedLikeFlow() {
        bindIssued();
        PermissionDataScopeHook hook = hook(decision("ledger", "read", new Decision(false, "no_grant")), null);
        PermissionException ex = assertThrows(
                PermissionException.class, () -> hook.bind("tenant-a", "ledger", "read"));
        assertEquals("P2.PERM.0003", ex.errorCode().code());
    }

    @Test
    void explicitDenyStillThrows() {
        bindIssued();
        PermissionDataScopeHook hook = hook(decision("ledger", "read", new Decision(false, "explicit_deny")), null);
        PermissionException ex = assertThrows(
                PermissionException.class, () -> hook.bind("tenant-a", "ledger", "read"));
        assertEquals("P2.PERM.0002", ex.errorCode().code());
    }

    @Test
    void seededGrantIsUsed() {
        bindIssued();
        TenantMemberGrantSeeder seeder = mock(TenantMemberGrantSeeder.class);
        PermissionService service = mock(PermissionService.class);
        when(service.decide("ledger", "read")).thenReturn(new Decision(true, "allowed"));
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        PermissionDataScopeHook hook = new PermissionDataScopeHook(module, provider(seeder));
        ScopedRead scoped = hook.bind("tenant-a", "ledger", "read");
        assertEquals("allowed", scoped.decision().reason());
        assertTrue(scoped.decision().allowed());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "sub", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static PermissionDataScopeHook hook(PermissionService service, TenantMemberGrantSeeder seeder) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        return new PermissionDataScopeHook(module, provider(seeder));
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
