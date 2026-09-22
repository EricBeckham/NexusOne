package one.nexus.bootstrap.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.file.FileRef;
import one.nexus.file.FileObjectReconciler;
import one.nexus.file.FileReconcileJob;
import one.nexus.file.FileService;
import one.nexus.file.FileStatus;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FileReconcileTaskHandlerTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";

    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("files.reconcile"), new one.nexus.isolation.audit.CollectingAuditSink());
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void tenantJobApplyMarksMissing() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-handler");
        CountingObjectStorage storage = new CountingObjectStorage();
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (s, r) -> true, clock, "nexus-files", 1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileReconcileJob job = new FileReconcileJob(new FileObjectReconciler(storage, repo, "nexus-files"));
        FileRef ref = service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");
        storage.dropQuietly("nexus-files", ref.objectKey());

        IsolatedEnvelope envelope = IsolatedEnvelope.fromCurrent(FileReconcileJob.TASK_TYPE, "{\"apply\":true}");
        new FileReconcileTaskHandler(job).execute(envelope);

        assertEquals(FileStatus.MISSING, repo.find(ref.fileId()).orElseThrow().status());
        assertTrue(job.lastResult().applied());
        assertEquals(1, job.lastResult().repaired());
    }

    @Test
    void handlerRejectsMissingContext() {
        FileReconcileJob job = new FileReconcileJob(new FileObjectReconciler(
                new CountingObjectStorage(), new InMemoryFileObjectRepository(), "nexus-files"));
        IsolatedEnvelope envelope = new IsolatedEnvelope(
                TENANT_A, "trace-none", FileReconcileJob.TASK_TYPE, "{\"apply\":false}");
        one.nexus.isolation.api.IsolationException ex = assertThrows(
                one.nexus.isolation.api.IsolationException.class,
                () -> new FileReconcileTaskHandler(job).execute(envelope));
        assertEquals(one.nexus.isolation.api.IsolationErrorCode.MISSING_CONTEXT, ex.error());
    }
}
