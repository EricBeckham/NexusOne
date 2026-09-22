package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
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
import one.nexus.task.TaskSubmitGuard;
import one.nexus.task.jdbc.JdbcTaskQueue;
import one.nexus.task.quartz.IsolatedJobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;

/**
 * Job / Task submit calls decide before isolation enqueue. Auth deny is not
 * {@code P1.M02.019}. Isolation-only (no P2 context) still reaches isolation.
 */
class G2AuthorizedSubmitTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void exportTaskAndJobSubmitDeniedBeforeIsolation() throws Exception {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER), true);
        TaskSubmitGuard guard = guard(harness);
        DataSource dataSource = mock(DataSource.class);
        ExecutionIsolation isolation = mock(ExecutionIsolation.class);
        when(dataSource.getConnection()).thenThrow(new AssertionError("JDBC must not run after deny"));
        when(isolation.envelopeFromCurrent(any(), any()))
                .thenThrow(new AssertionError("isolation must not run after deny"));

        JdbcTaskQueue queue = new JdbcTaskQueue(
                dataSource, isolation, ExecutionSettings.defaults(), Clock.systemUTC(), guard);
        PermissionException task = assertThrows(
                PermissionException.class, () -> queue.submit(IsolatedExport.TASK_TYPE, "{}"));
        assertEquals("P2.PERM.0003", task.errorCode().code());
        verify(isolation, never()).envelopeFromCurrent(any(), any());
        verify(dataSource, never()).getConnection();

        Scheduler scheduler = mock(Scheduler.class);
        IsolatedJobScheduler jobs = new IsolatedJobScheduler(scheduler, isolation, guard);
        PermissionException job = assertThrows(
                PermissionException.class,
                () -> jobs.scheduleOnce("g2-export", Instant.now(), IsolatedExport.TASK_TYPE, "{}"));
        assertEquals("P2.PERM.0003", job.errorCode().code());
        verify(scheduler, never()).scheduleJob(any(), any());
        verify(isolation, never()).requireTenantMembership();
    }

    @Test
    void genericTaskAndJobSubmitDeniedWithoutExportGrant() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER), true);
        TaskSubmitGuard guard = guard(harness);
        ExecutionIsolation isolation = mock(ExecutionIsolation.class);
        when(isolation.envelopeFromCurrent(any(), any()))
                .thenThrow(new AssertionError("isolation must not run after deny"));
        JdbcTaskQueue queue = new JdbcTaskQueue(
                mock(DataSource.class), isolation, ExecutionSettings.defaults(), Clock.systemUTC(), guard);
        PermissionException task = assertThrows(
                PermissionException.class, () -> queue.submit("cleanup", "{}"));
        assertEquals("no_grant", task.getMessage());

        IsolatedJobScheduler jobs = new IsolatedJobScheduler(mock(Scheduler.class), isolation, guard);
        PermissionException job = assertThrows(
                PermissionException.class,
                () -> jobs.scheduleCron("g2-job", "0 0 * * * ?", "cleanup", "{}"));
        assertEquals("no_grant", job.getMessage());
        verify(isolation, never()).envelopeFromCurrent(any(), any());
    }

    @Test
    void grantedJobSubmitReachesIsolation() throws Exception {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER), true);
        TaskSubmitGuard guard = guard(harness);
        ExecutionIsolation isolation = mock(ExecutionIsolation.class);
        IsolatedEnvelope envelope = new IsolatedEnvelope(
                harness.tenantId.toString(), "trace-job", IsolatedExport.TASK_TYPE, "{}");
        when(isolation.envelopeFromCurrent(IsolatedExport.TASK_TYPE, "{}")).thenReturn(envelope);
        when(isolation.requireTenantMembership()).thenReturn(
                new StoredMembership(harness.tenantId.toString(), "actor", "PERSON", "mem"));
        Scheduler scheduler = mock(Scheduler.class);
        IsolatedJobScheduler jobs = new IsolatedJobScheduler(scheduler, isolation, guard);
        jobs.scheduleOnce("g2-ok", Instant.now(), IsolatedExport.TASK_TYPE, "{}");
        verify(isolation).envelopeFromCurrent(IsolatedExport.TASK_TYPE, "{}");
        verify(scheduler).scheduleJob(any(), any());
    }

    private static TaskSubmitGuard guard(Harness harness) {
        return new TaskSubmitGuard() {
            @Override
            public void assertTask(String taskType) {
                AuthorizedSubmit.task(harness.module.service(), taskType);
            }

            @Override
            public void assertJob(String payloadType) {
                AuthorizedSubmit.job(harness.module.service(), payloadType);
            }
        };
    }

    private record Harness(PermissionModule module, UUID tenantId) {
        static Harness open(Set<String> roles, boolean installExample) {
            UUID id = UUID.fromString("55555555-5555-4555-8555-555555555555");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-submit", Instant.now(), Instant.now().plusSeconds(60)));
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
            return new Harness(module, id);
        }
    }
}
