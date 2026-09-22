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
import one.nexus.permission.surface.DumpSurfaceCatalog;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DumpSurfaceGuardTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void missingP2ContextFallsBackToTenantOnly() {
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION,
                        new Decision(false, "no_grant")),
                null);
        Decision decision = guard.requireDump();
        assertEquals("tenant_only", decision.reason());
        assertTrue(decision.allowed());
    }

    @Test
    void noGrantIsDeniedLikeDesktopExport() {
        bindIssued();
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION,
                        new Decision(false, "no_grant")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireDump);
        assertEquals("P2.PERM.0003", ex.errorCode().code());
        assertEquals("no_grant", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void explicitDenyIs403OnDump() {
        bindIssued();
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION,
                        new Decision(false, "explicit_deny")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireDump);
        assertEquals("P2.PERM.0002", ex.errorCode().code());
        assertEquals("explicit_deny", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void tenantBoundaryIs403OnRead() {
        bindIssued();
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION,
                        new Decision(false, "tenant_boundary")),
                null);
        PermissionException ex = assertThrows(PermissionException.class, guard::requireRead);
        assertEquals("P2.PERM.0001", ex.errorCode().code());
        assertEquals("tenant_boundary", ex.getMessage());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void decideDumpDoesNotThrowOnNoGrant() {
        bindIssued();
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION,
                        new Decision(false, "no_grant")),
                null);
        Decision decision = guard.decideDump();
        assertEquals("no_grant", decision.reason());
        assertEquals(false, decision.allowed());
    }

    @Test
    void requireDumpEmitsAndDecideDoesNot() {
        bindIssued();
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        DumpSurfaceGuard guard = guard(
                decision(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION,
                        new Decision(false, "no_grant")),
                null,
                audits);
        TraceIds.putMdc("0199a1c0-0000-7000-8000-00000000c237");
        Decision peeked = guard.decideDump();
        assertEquals("no_grant", peeked.reason());
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c237").isEmpty(),
                "GET /decision twin must not write deny");
        assertThrows(PermissionException.class, guard::requireDump);
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c237").stream()
                .anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())));
        assertTrue(audits.findByTraceId("0199a1c0-0000-7000-8000-00000000c237").stream()
                .anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())));
        assertTrue(audits.verifyChain().intact());
    }

    private static void bindIssued() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        TenantContextHolder.set(new TenantContext(
                id, id, id, id, "sub", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static DumpSurfaceGuard guard(PermissionService service, TenantMemberGrantSeeder seeder) {
        return guard(service, seeder, null);
    }

    private static DumpSurfaceGuard guard(
            PermissionService service, TenantMemberGrantSeeder seeder, InMemoryHashChainedAuditPort audits) {
        PermissionModule module = mock(PermissionModule.class);
        when(module.service()).thenReturn(service);
        when(module.audits()).thenReturn(audits);
        return new DumpSurfaceGuard(module, provider(seeder));
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
