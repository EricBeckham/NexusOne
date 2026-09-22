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
import one.nexus.p3.flow.FlowException;
import one.nexus.p3.port.DataScopeHook.ScopedRead;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.admin.RoleMatrixService;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * List/detail hook and flow {@link SurfaceGuard} must agree on the same
 * {@code PermissionService} Decision. {@code tenant_member} is not installed.
 */
class G2SurfaceAgreementTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void noGrantIsDenyOnListDetailAndFlow() {
        Harness harness = Harness.open(Set.of());
        PermissionException list = assertThrows(
                PermissionException.class, () -> harness.hook.bind(harness.tenantId, "ledger", "read"));
        assertEquals("P2.PERM.0003", list.errorCode().code());
        PermissionException flow = assertThrows(
                PermissionException.class,
                () -> SurfaceGuard.require(harness.module.service(), "process.instance", "start"));
        assertEquals("no_grant", flow.getMessage());
        assertEquals(list.getMessage(), flow.getMessage());
    }

    @Test
    void selfReaderAllowsScopedListAndStillDeniesFlowStart() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        ScopedRead list = harness.hook.bind(harness.tenantId, "ledger", "read");
        ScopedRead detail = harness.hook.bind(harness.tenantId, "ledger", "read");
        assertTrue(list.decision().allowed());
        assertEquals(list.decision().policyVersion(), detail.decision().policyVersion());
        assertEquals(list.decision().scope(), detail.decision().scope());
        assertTrue(list.query().whereSql().contains("owner_id"));
        assertEquals(list.query().whereSql(), detail.query().whereSql());
        PermissionException start = assertThrows(
                PermissionException.class,
                () -> SurfaceGuard.require(harness.module.service(), "process.instance", "start"));
        assertEquals("no_grant", start.getMessage());
        FlowException wrapped = assertThrows(FlowException.class, () -> {
            try {
                SurfaceGuard.require(harness.module.service(), "process.task", "approve");
            } catch (PermissionException ex) {
                throw FlowException.forbidden(ex.getMessage());
            }
        });
        assertEquals("no_grant", wrapped.getMessage());
    }

    @Test
    void approverAllowsFlowOnSameEngine() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.APPROVER));
        RoleMatrixService.MatrixView matrix = harness.module.matrix().installExample();
        assertTrue(matrix.differentiatedPolicy());
        assertTrue(!matrix.demoSeedPresent());
        SurfaceGuard.require(harness.module.service(), "process.task", "approve");
        SurfaceGuard.require(harness.module.service(), "process.instance", "start");
        PermissionException read = assertThrows(
                PermissionException.class, () -> harness.hook.bind(harness.tenantId, "ledger", "read"));
        assertEquals("no_grant", read.getMessage());
    }

    private record Harness(PermissionModule module, PermissionDataScopeHook hook, String tenantId) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("33333333-3333-4333-8333-333333333333");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-sub", Instant.now(), Instant.now().plusSeconds(60)));
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
            return new Harness(module, new PermissionDataScopeHook(module, provider), id.toString());
        }
    }
}
