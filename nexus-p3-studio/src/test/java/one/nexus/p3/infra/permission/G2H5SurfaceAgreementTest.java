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
import one.nexus.permission.surface.H5SurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * H5 list/detail/form/todo {@link H5SurfaceGuard} must agree with desktop
 * list/detail/form/flow on the same {@code PermissionService} Decision.
 */
class G2H5SurfaceAgreementTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void noGrantIsDenyOnH5ListFormAndTodo() {
        Harness harness = Harness.open(Set.of());
        PermissionException list = assertThrows(
                PermissionException.class, () -> harness.hook.bind(harness.tenantId, "ledger", "read"));
        PermissionException h5List = assertThrows(PermissionException.class, harness.h5::requireListRead);
        PermissionException h5Form = assertThrows(PermissionException.class, harness.h5::requireFormRead);
        PermissionException h5Fill = assertThrows(PermissionException.class, harness.h5::requireFormWrite);
        PermissionException h5Todo = assertThrows(PermissionException.class, harness.h5::requireTodoRead);
        PermissionException h5Approve = assertThrows(PermissionException.class, harness.h5::requireTodoApprove);
        assertEquals("P2.PERM.0003", list.errorCode().code());
        assertEquals(list.getMessage(), h5List.getMessage());
        assertEquals(list.getMessage(), h5Form.getMessage());
        assertEquals(list.getMessage(), h5Fill.getMessage());
        assertEquals(list.getMessage(), h5Todo.getMessage());
        assertEquals(list.getMessage(), h5Approve.getMessage());
        assertEquals("P2.PERM.0003", h5List.errorCode().code());
        assertEquals("P2.PERM.0003", h5Form.errorCode().code());
        assertEquals("P2.PERM.0003", h5Todo.errorCode().code());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(
                new one.nexus.permission.engine.Decision(false, "no_grant")).get("engine"));
    }

    @Test
    void selfReaderAllowsH5ListAndFormReadAndDeniesWritesAndTodos() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        ScopedRead desktop = harness.hook.bind(harness.tenantId, "ledger", "read");
        assertTrue(harness.h5.requireListRead().allowed());
        assertTrue(harness.h5.requireFormRead().allowed());
        assertEquals(desktop.decision().policyVersion(), harness.h5.requireListRead().policyVersion());
        PermissionException write = assertThrows(PermissionException.class, harness.h5::requireFormWrite);
        PermissionException todo = assertThrows(PermissionException.class, harness.h5::requireTodoRead);
        assertEquals("no_grant", write.getMessage());
        assertEquals("P2.PERM.0003", write.errorCode().code());
        assertEquals(write.getMessage(), todo.getMessage());
    }

    @Test
    void approverAllowsH5TodoReadAndWritesOnSameEngine() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.APPROVER));
        harness.module.matrix().installExample();
        assertTrue(harness.h5.requireTodoRead().allowed());
        assertTrue(harness.h5.requireTodoApprove().allowed());
        assertTrue(harness.h5.requireTodoReject().allowed());
        SurfaceGuard.require(harness.module.service(),
                H5SurfaceCatalog.INSTANCE_RESOURCE, H5SurfaceCatalog.INSTANCE_START);
        PermissionException list = assertThrows(PermissionException.class, harness.h5::requireListRead);
        PermissionException form = assertThrows(PermissionException.class, harness.h5::requireFormWrite);
        assertEquals("no_grant", list.getMessage());
        assertEquals(list.getMessage(), form.getMessage());
        assertEquals("P2.PERM.0003", list.errorCode().code());
    }

    @Test
    void formDesignerAllowsH5FormAndStillDeniesListAndTodo() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        harness.module.matrix().installExample();
        assertTrue(harness.h5.requireFormRead().allowed());
        assertTrue(harness.h5.requireFormWrite().allowed());
        PermissionException list = assertThrows(PermissionException.class, harness.h5::requireListRead);
        PermissionException todo = assertThrows(PermissionException.class, harness.h5::requireTodoApprove);
        assertEquals("P2.PERM.0003", list.errorCode().code());
        assertEquals(list.getMessage(), todo.getMessage());
    }

    private record Harness(
            PermissionModule module,
            PermissionDataScopeHook hook,
            FormSurfaceGuard forms,
            H5SurfaceGuard h5,
            String tenantId) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("44444444-4444-4444-8444-444444444444");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-h5", Instant.now(), Instant.now().plusSeconds(60)));
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
                    new FormSurfaceGuard(module, provider),
                    new H5SurfaceGuard(module, provider),
                    id.toString());
        }
    }
}
