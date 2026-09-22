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
import one.nexus.permission.surface.H5SurfaceCatalog;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class H5SurfaceGuardTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        H5SurfaceGuard guard = guard(decision("ledger", "read", new Decision(false, "no_grant")), null);
        Decision decision = guard.requireListRead();
        assertEquals("tenant_only", decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantIsDeniedLikeDesktopList() {
        bindIssued();
        H5SurfaceGuard guard = guard(decision("ledger", "read", new Decision(false, "no_grant")), null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireListRead);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void explicitDenyIs403OnFormWrite() {
        bindIssued();
        H5SurfaceGuard guard = guard(decision("form", "write", new Decision(false, "explicit_deny")), null);
        PermissionException write = assertThrows(PermissionException.class, guard::requireFormWrite);
        assertEquals("P2.PERM.0002", write.errorCode().code());
        assertEquals("explicit_deny", write.getMessage());
        assertEquals(403, write.httpStatus());
    }

    @Test
    void tenantBoundaryIs403OnTodoRead() {
        bindIssued();
        H5SurfaceGuard guard = guard(
                decision(H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ,
                        new Decision(false, "tenant_boundary")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireTodoRead);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void todoWritesShareDesktopCodes() {
        bindIssued();
        H5SurfaceGuard approve = guard(
                decision("process.task", "approve", new Decision(false, "no_grant")), null);
        H5SurfaceGuard reject = guard(
                decision("process.task", "reject", new Decision(false, "explicit_deny")), null);
        H5SurfaceGuard withdraw = guard(
                decision("process.instance", "withdraw", new Decision(false, "tenant_boundary")), null);
        PermissionException noGrant = assertThrows(PermissionException.class, approve::requireTodoApprove);
        PermissionException deny = assertThrows(PermissionException.class, reject::requireTodoReject);
        PermissionException boundary = assertThrows(PermissionException.class, withdraw::requireTodoWithdraw);
        assertEquals("P2.PERM.0003", noGrant.errorCode().code());
        assertEquals("P2.PERM.0002", deny.errorCode().code());
        assertEquals("P2.PERM.0001", boundary.errorCode().code());
    }

    @Test
    void noGrantEmitsPermissionDeniedAndGuardReject() {
        bindIssued();
        TraceIds.putMdc("0199a1c0-0000-7000-8000-00000000c235");
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        H5SurfaceGuard guard = guard(decision("ledger", "read", new Decision(false, "no_grant")), null, audits);
        assertThrows(PermissionException.class, guard::requireListRead);
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c235").stream()
                .anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())));
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c235").stream()
                .anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())));
        assertTrue(audits.verifyChain().intact());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "sub", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static H5SurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        return guard(service, seeder, null);
    }

    private static H5SurfaceGuard guard(
            PermissionService service, TenantMemberGrantSeeder seeder, InMemoryHashChainedAuditPort audits) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        when(module.audits()).thenReturn(audits);
        return new H5SurfaceGuard(module, provider(seeder));
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
