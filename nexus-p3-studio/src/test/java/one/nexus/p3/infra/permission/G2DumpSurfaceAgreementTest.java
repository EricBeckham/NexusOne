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
import one.nexus.permission.surface.DumpSurfaceCatalog;
import one.nexus.permission.surface.SurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Physical dump / raw export {@link DumpSurfaceGuard} must agree with desktop
 * list and AuthorizedExport on the same {@code PermissionService} Decision.
 */
class G2DumpSurfaceAgreementTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void noGrantIsDenyOnDumpAndReadLikeList() {
        Harness harness = Harness.open(Set.of());
        PermissionException list = assertThrows(
                PermissionException.class, () -> harness.hook.bind(harness.tenantId, "ledger", "read"));
        PermissionException read = assertThrows(PermissionException.class, harness.dumps::requireRead);
        PermissionException dump = assertThrows(PermissionException.class, harness.dumps::requireDump);
        PermissionException catalog = assertThrows(
                PermissionException.class,
                () -> SurfaceGuard.require(harness.module.service(),
                        SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION));
        assertEquals("P2.PERM.0003", list.errorCode().code());
        assertEquals(list.getMessage(), read.getMessage());
        assertEquals("no_grant", dump.getMessage());
        assertEquals(dump.getMessage(), catalog.getMessage());
        assertEquals("P2.PERM.0003", read.errorCode().code());
        assertEquals("P2.PERM.0003", dump.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(
                new one.nexus.permission.engine.Decision(false, "no_grant")).get("engine"));
    }

    @Test
    void selfReaderAllowsReadAndDeniesDump() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        ScopedRead desktop = harness.hook.bind(harness.tenantId, "ledger", "read");
        assertTrue(harness.dumps.requireRead().allowed());
        assertEquals(desktop.decision().policyVersion(), harness.dumps.requireRead().policyVersion());
        PermissionException dump = assertThrows(PermissionException.class, harness.dumps::requireDump);
        assertEquals("no_grant", dump.getMessage());
        assertEquals("P2.PERM.0003", dump.errorCode().code());
    }

    @Test
    void deptManagerAllowsReadAndDumpOnSameEngine() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER));
        harness.module.matrix().installExample();
        assertTrue(harness.dumps.requireRead().allowed());
        assertTrue(harness.dumps.requireDump().allowed());
        SurfaceGuard.require(harness.module.service(),
                DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION);
    }

    @Test
    void formDesignerStillDeniesReadAndDump() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        harness.module.matrix().installExample();
        PermissionException read = assertThrows(PermissionException.class, harness.dumps::requireRead);
        PermissionException dump = assertThrows(PermissionException.class, harness.dumps::requireDump);
        assertEquals("P2.PERM.0003", read.errorCode().code());
        assertEquals(read.getMessage(), dump.getMessage());
    }

    private record Harness(
            PermissionModule module,
            PermissionDataScopeHook hook,
            DumpSurfaceGuard dumps,
            String tenantId) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("55555555-5555-4555-8555-555555555555");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-dump", Instant.now(), Instant.now().plusSeconds(60)));
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
                    new DumpSurfaceGuard(module, provider),
                    id.toString());
        }
    }
}
