package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
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
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Authorization deny is {@code P2.PERM.0003} and happens before IsolatedExport
 * sinks. Cross-tenant isolation stays {@code P1.M02.019}.
 */
class G2AuthorizedExportTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("ops.summary"), sink);

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
    }

    @Test
    void noGrantNeverRunsExportFileCacheOrDispatchSinks() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER), true);
        bindAccess(harness.tenantCode);
        AtomicBoolean file = new AtomicBoolean(false);
        AtomicBoolean cache = new AtomicBoolean(false);
        AtomicBoolean dispatched = new AtomicBoolean(false);

        PermissionException submit = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.submit(harness.module.service(), harness.tenantCode, "{}"));
        PermissionException write = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.writeFile(
                        harness.module.service(), harness.tenantCode, "export/x.csv", new byte[] {1},
                        key -> file.set(true)));
        PermissionException cacheEx = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.cacheStatus(
                        harness.module.service(), harness.tenantCode, "dev", "job-1", "PENDING",
                        (k, v) -> cache.set(true)));
        IsolatedEnvelope stolen = new IsolatedEnvelope(
                harness.tenantCode, "trace-stolen", IsolatedExport.TASK_TYPE, "{}");
        PermissionException dispatch = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.dispatch(
                        harness.module.service(), stolen, harness.tenantCode, item -> dispatched.set(true)));

        assertEquals("P2.PERM.0003", submit.errorCode().code());
        assertEquals("no_grant", submit.getMessage());
        assertEquals(submit.errorCode().code(), write.errorCode().code());
        assertEquals(submit.errorCode().code(), cacheEx.errorCode().code());
        assertEquals(submit.errorCode().code(), dispatch.errorCode().code());
        assertFalse(file.get());
        assertFalse(cache.get());
        assertFalse(dispatched.get());
        assertEquals(0, sink.rejectCount());
        assertEquals(0, sink.exportAccepted().size());
    }

    @Test
    void grantedExportRunsSinksThenCrossTenantIsIsolationNotPermission() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER), true);
        bindAccess(harness.tenantCode);
        AtomicReference<String> fileKey = new AtomicReference<>();
        AtomicReference<String> cacheKey = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean(false);

        IsolatedEnvelope envelope = AuthorizedExport.submit(
                harness.module.service(), harness.tenantCode, "{\"ledger\":\"leave\"}");
        assertEquals(IsolatedExport.TASK_TYPE, envelope.payloadType());
        AuthorizedExport.dispatch(harness.module.service(), envelope, harness.tenantCode, item -> ran.set(true));
        AuthorizedExport.writeFile(
                harness.module.service(), harness.tenantCode, "export/g2.csv", new byte[] {1}, fileKey::set);
        AuthorizedExport.cacheStatus(
                harness.module.service(), harness.tenantCode, "dev", "job-1", "PENDING", (k, v) -> cacheKey.set(k));
        assertTrue(ran.get());
        assertEquals("tenants/" + harness.tenantCode + "/export/g2.csv", fileKey.get());
        assertEquals("nexus:dev:" + harness.tenantCode + ":export:job-1", cacheKey.get());
        assertEquals(0, sink.rejectCount());

        AtomicBoolean foreignFile = new AtomicBoolean(false);
        IsolationException cross = assertThrows(
                IsolationException.class,
                () -> AuthorizedExport.writeFile(
                        harness.module.service(), "beta", "export/x.csv", new byte[] {1},
                        key -> foreignFile.set(true)));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, cross.error());
        assertEquals("P1.M02.019", cross.error().code());
        assertFalse(foreignFile.get());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
    }

    private void bindAccess(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }

    private record Harness(PermissionModule module, String tenantCode) {
        static Harness open(Set<String> roles, boolean installExample) {
            UUID id = UUID.fromString("44444444-4444-4444-8444-444444444444");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-export", Instant.now(), Instant.now().plusSeconds(60)));
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
            if (installExample) {
                module.matrix().installExample();
            }
            return new Harness(module, id.toString());
        }
    }
}
