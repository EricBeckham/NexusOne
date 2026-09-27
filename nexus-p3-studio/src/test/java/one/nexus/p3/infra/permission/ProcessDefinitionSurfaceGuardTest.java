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
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProcessDefinitionSurfaceGuardTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void platformSystemAdminUsesExistingConfigPermissionAndDeniesStillWin(boolean denied) {
        bindIssued(); var context=TenantContextHolder.require();
        PermissionService service=mock(PermissionService.class);
        when(service.currentSubject()).thenReturn(new one.nexus.permission.engine.Subject(context.tenantId().toString(),context.personId().toString(),
                java.util.Set.of(one.nexus.permission.admin.PlatformDutyCatalog.SYSTEM_ADMIN),java.util.Set.of(),java.util.Set.of()));
        when(service.snapshot()).thenReturn(new one.nexus.permission.engine.PolicySnapshot(context.tenantId().toString(),1));
        when(service.decide("tenant.config","update")).thenReturn(new Decision(!denied,denied?"explicit_deny":"granted"));
        var properties=new one.nexus.p1.config.P1Properties(); properties.getPlatformTenant().setId(context.tenantId());
        var guard=guard(service,null); guard.setPlatformProperties(properties);
        if(denied) assertThrows(PermissionException.class,guard::requireWrite); else assertTrue(guard.requireWrite().allowed());
        org.mockito.Mockito.verify(service).decide("tenant.config","update");
    }
    @Test void platformAuditDutyCannotWriteDefaultsEvenWithConfigGrant() {
        bindIssued(); var context=TenantContextHolder.require(); var service=mock(PermissionService.class);
        when(service.currentSubject()).thenReturn(new one.nexus.permission.engine.Subject(context.tenantId().toString(),context.personId().toString(),
                java.util.Set.of(one.nexus.permission.admin.PlatformDutyCatalog.AUDIT_ADMIN),java.util.Set.of(),java.util.Set.of()));
        when(service.snapshot()).thenReturn(new one.nexus.permission.engine.PolicySnapshot(context.tenantId().toString(),1));
        var properties=new one.nexus.p1.config.P1Properties(); properties.getPlatformTenant().setId(context.tenantId());
        var guard=guard(service,null); guard.setPlatformProperties(properties);
        assertThrows(PermissionException.class,guard::requireWrite);
    }
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        ProcessDefinitionSurfaceGuard guard = guard(decision(new Decision(false, "no_grant")), null);
        Decision decision = guard.requireRead();
        assertEquals(IsolationOnlyDecision.REASON, decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantDeniesWriteBeforePublish() {
        bindIssued();
        ProcessDefinitionSurfaceGuard guard = guard(decision(new Decision(false, "no_grant")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireWrite);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void otherTenantBoundaryIsDenied() {
        bindIssued();
        ProcessDefinitionSurfaceGuard guard = guard(decision(new Decision(false, "tenant_boundary")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireRead);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "process-definition", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static ProcessDefinitionSurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        return new ProcessDefinitionSurfaceGuard(module, provider(seeder));
    }

    private static PermissionService decision(Decision decision) {
        PermissionService service = mock(PermissionService.class);
        when(service.decide(ProcessDefinitionSurfaceGuard.RESOURCE, ProcessDefinitionSurfaceGuard.READ))
                .thenReturn(decision);
        when(service.decide(ProcessDefinitionSurfaceGuard.RESOURCE, ProcessDefinitionSurfaceGuard.WRITE))
                .thenReturn(decision);
        return service;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantMemberGrantSeeder> provider(TenantMemberGrantSeeder seeder) {
        ObjectProvider<TenantMemberGrantSeeder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(seeder);
        return provider;
    }
}
