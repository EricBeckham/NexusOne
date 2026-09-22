package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.CatalogSurfaceCatalog;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Catalog definition HTTP {@link CatalogSurfaceGuard} must agree with desktop
 * list on the same {@code PermissionService} Decision. Issued P2 context
 * cannot emit {@code tenant_only}.
 */
class G2CatalogSurfaceAgreementTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void noGrantIsDenyOnCatalogLikeList() {
        Harness harness = Harness.open(Set.of());
        PermissionException list = assertThrows(
                PermissionException.class, () -> harness.hook.bind(harness.tenantId, "ledger", "read"));
        PermissionException catalog = assertThrows(PermissionException.class, harness.catalogs::requireLedgerRead);
        PermissionException app = assertThrows(PermissionException.class, harness.catalogs::requireApplicationWrite);
        PermissionException types = assertThrows(PermissionException.class, harness.catalogs::requireBusinessTypeRead);
        assertEquals("P2.PERM.0003", list.errorCode().code());
        assertEquals(list.getMessage(), catalog.getMessage());
        assertEquals(list.getMessage(), app.getMessage());
        assertEquals(list.getMessage(), types.getMessage());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(
                new one.nexus.permission.engine.Decision(false, "no_grant")).get("engine"));
        PermissionException skip = assertThrows(PermissionException.class, IsolationOnlyDecision::allow);
        assertEquals(IsolationOnlyDecision.FORBIDDEN, skip.getMessage());
    }

    @Test
    void selfReaderAllowsCatalogReadAndDeniesWrite() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        ScopedRead desktop = harness.hook.bind(harness.tenantId, "ledger", "read");
        assertTrue(harness.catalogs.requireLedgerRead().allowed());
        assertEquals(desktop.decision().policyVersion(), harness.catalogs.requireApplicationRead().policyVersion());
        PermissionException write = assertThrows(PermissionException.class, harness.catalogs::requireLedgerWrite);
        assertEquals("no_grant", write.getMessage());
        assertEquals("P2.PERM.0003", write.errorCode().code());
    }

    @Test
    void deptManagerAllowsCatalogReadAndWriteOnSameEngine() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER));
        harness.module.matrix().installExample();
        assertTrue(harness.catalogs.requireLedgerRead().allowed());
        assertTrue(harness.catalogs.requireLedgerWrite().allowed());
        SurfaceGuard.require(harness.module.service(),
                CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_WRITE);
    }

    @Test
    void formDesignerStillDeniesCatalogReadAndWrite() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        harness.module.matrix().installExample();
        PermissionException read = assertThrows(PermissionException.class, harness.catalogs::requireLedgerRead);
        PermissionException write = assertThrows(PermissionException.class, harness.catalogs::requireBusinessTypeWrite);
        assertEquals("P2.PERM.0003", read.errorCode().code());
        assertEquals(read.getMessage(), write.getMessage());
    }

    private record Harness(
            PermissionModule module,
            PermissionDataScopeHook hook,
            CatalogSurfaceGuard catalogs,
            String tenantId) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("77777777-7777-4777-8777-777777777777");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-catalog", Instant.now(), Instant.now().plusSeconds(60)));
            AtomicReference<Set<String>> roleRef = new AtomicReference<>(roles);
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(TenantContext context) {
                    return new Subject(
                            context.tenantId().toString(),
                            context.personId().toString(),
                            roleRef.get(),
                            Set.of(),
                            Set.of("d1"));
                }

                @Override
                public DepartmentCatalog catalog(UUID tenantId) {
                    return DepartmentCatalog.EMPTY;
                }
            };
            SnapshotManager snapshots = new SnapshotManager(
                    tenant -> repository.currentVersion(UUID.fromString(tenant)),
                    tenant -> repository.loadSnapshot(UUID.fromString(tenant)));
            PermissionModule module = new PermissionModule(
                    repository, subjects, snapshots, new InMemoryPolicyInvalidation());
            @SuppressWarnings("unchecked")
            ObjectProvider<one.nexus.permission.admin.TenantMemberGrantSeeder> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(null);
            return new Harness(
                    module,
                    new PermissionDataScopeHook(module, provider),
                    new CatalogSurfaceGuard(module, provider),
                    id.toString());
        }
    }
}
