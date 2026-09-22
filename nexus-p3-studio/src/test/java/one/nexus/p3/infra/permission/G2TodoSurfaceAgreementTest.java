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
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.H5SurfaceCatalog;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.surface.TodoSurfaceCatalog;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Todo list HTTP {@link TodoSurfaceGuard} must agree with H5
 * {@code process.task}/{@code read} on the same {@code PermissionService}
 * Decision. Issued P2 context cannot emit {@code tenant_only}.
 */
class G2TodoSurfaceAgreementTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void noGrantIsDenyOnTodoLikeH5() {
        Harness harness = Harness.open(Set.of());
        PermissionException h5 = assertThrows(PermissionException.class, harness.h5::requireTodoRead);
        PermissionException todo = assertThrows(PermissionException.class, harness.todos::requireTodoRead);
        assertEquals("P2.PERM.0003", h5.errorCode().code());
        assertEquals(h5.getMessage(), todo.getMessage());
        assertEquals("no_grant", todo.getMessage());
        assertEquals("PermissionEngine", SurfaceGuard.asApi(
                new one.nexus.permission.engine.Decision(false, "no_grant")).get("engine"));
        PermissionException skip = assertThrows(PermissionException.class, IsolationOnlyDecision::allow);
        assertEquals(IsolationOnlyDecision.FORBIDDEN, skip.getMessage());
    }

    @Test
    void selfReaderDeniesTodoReadOnBothGuards() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        PermissionException h5 = assertThrows(PermissionException.class, harness.h5::requireTodoRead);
        PermissionException todo = assertThrows(PermissionException.class, harness.todos::requireTodoRead);
        assertEquals("P2.PERM.0003", h5.errorCode().code());
        assertEquals(h5.getMessage(), todo.getMessage());
    }

    @Test
    void approverAllowsTodoReadOnSameEngine() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.APPROVER));
        harness.module.matrix().installExample();
        assertTrue(harness.todos.requireTodoRead().allowed());
        assertTrue(harness.h5.requireTodoRead().allowed());
        SurfaceGuard.require(harness.module.service(),
                TodoSurfaceCatalog.TODO_RESOURCE, TodoSurfaceCatalog.TODO_READ);
        SurfaceGuard.require(harness.module.service(),
                H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ);
    }

    @Test
    void formDesignerStillDeniesTodoRead() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        harness.module.matrix().installExample();
        PermissionException h5 = assertThrows(PermissionException.class, harness.h5::requireTodoRead);
        PermissionException todo = assertThrows(PermissionException.class, harness.todos::requireTodoRead);
        assertEquals("P2.PERM.0003", h5.errorCode().code());
        assertEquals(h5.getMessage(), todo.getMessage());
    }

    private record Harness(
            PermissionModule module,
            H5SurfaceGuard h5,
            TodoSurfaceGuard todos,
            String tenantId) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("88888888-8888-4888-8888-888888888888");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-todo", Instant.now(), Instant.now().plusSeconds(60)));
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
                    new H5SurfaceGuard(module, provider),
                    new TodoSurfaceGuard(module, provider),
                    id.toString());
        }
    }
}
