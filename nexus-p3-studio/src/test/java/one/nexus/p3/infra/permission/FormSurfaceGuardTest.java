package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FormSurfaceGuardTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        FormSurfaceGuard guard = guard(decision("form", "read", new Decision(false, "no_grant")), null);
        Decision decision = guard.requireRead();
        assertEquals("tenant_only", decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantIsDeniedLikeLedgerAndFlow() {
        bindIssued();
        FormSurfaceGuard guard = guard(decision("form", "read", new Decision(false, "no_grant")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireRead);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void explicitDenyIs403() {
        bindIssued();
        FormSurfaceGuard guard = guard(decision("form", "write", new Decision(false, "explicit_deny")), null);
        PermissionException write = assertThrows(PermissionException.class, guard::requireWrite);
        PermissionException fill = assertThrows(PermissionException.class, guard::requireFill);
        assertEquals("P2.PERM.0002", write.errorCode().code());
        assertEquals("explicit_deny", write.getMessage());
        assertEquals(403, write.httpStatus());
        assertEquals(write.errorCode().code(), fill.errorCode().code());
        assertEquals(write.getMessage(), fill.getMessage());
    }

    @Test
    void tenantBoundaryIs403() {
        bindIssued();
        FormSurfaceGuard guard = guard(decision("form", "read", new Decision(false, "tenant_boundary")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireRead);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void fillTenantBoundaryIs403BeforeWrite() {
        bindIssued();
        FormSurfaceGuard guard = guard(decision("form", "write", new Decision(false, "tenant_boundary")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireFill);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void seededGrantIsUsed() {
        bindIssued();
        TenantMemberGrantSeeder seeder = mock(TenantMemberGrantSeeder.class);
        PermissionService service = mock(PermissionService.class);
        when(service.decide("form", "write")).thenReturn(new Decision(true, "allowed"));
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        FormSurfaceGuard guard = new FormSurfaceGuard(module, provider(seeder));
        Decision decision = guard.requireFill();
        assertEquals("allowed", decision.reason());
        assertTrue(decision.allowed());
        assertEquals(decision.reason(), guard.requireWrite().reason());
    }

    @Test
    void noGrantEmitsPermissionDeniedAndGuardReject() {
        bindIssued();
        TraceIds.putMdc("0199a1c0-0000-7000-8000-00000000c234");
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        FormSurfaceGuard guard = guard(decision("form", "read", new Decision(false, "no_grant")), null, audits);
        assertThrows(PermissionException.class, guard::requireRead);
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c234").stream()
                .anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())));
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c234").stream()
                .anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())));
        assertTrue(audits.verifyChain().intact());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "sub", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static FormSurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        return guard(service, seeder, null);
    }

    private static FormSurfaceGuard guard(
            PermissionService service, TenantMemberGrantSeeder seeder, InMemoryHashChainedAuditPort audits) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        when(module.audits()).thenReturn(audits);
        return new FormSurfaceGuard(module, provider(seeder));
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
