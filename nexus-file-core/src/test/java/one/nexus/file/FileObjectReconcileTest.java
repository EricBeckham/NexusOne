package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.file.FileRef;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FileObjectReconcileTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";
    private static final String UNKNOWN = "33333333-3333-4333-8333-333333333333";

    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("files.reconcile"), sink);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void previewMatchesUploadedObject() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-match");
        Fixture fixture = fixture();
        FileRef ref = fixture.service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");

        FileObjectReconciler.Report report = fixture.job.run(false);
        assertFalse(report.applied());
        assertEquals("files.reconcile", report.path());
        assertEquals(1, report.matched());
        assertEquals(0, report.missing());
        assertEquals(0, report.orphan());
        assertEquals(0, report.repaired());
        assertEquals(0, fixture.storage.gets());
        assertEquals(0, fixture.storage.signs());
        assertEquals(ref.objectKey(), report.findings().getFirst().objectKey());
    }

    @Test
    void dryRunLeavesMissingObjectAndApplyMarksMetadata() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-missing");
        Fixture fixture = fixture();
        FileRef ref = fixture.service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");
        fixture.storage.dropQuietly("nexus-files", ref.objectKey());

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertEquals(1, preview.missing());
        assertEquals(0, preview.repaired());
        assertEquals(FileStatus.UPLOADED, fixture.repo.find(ref.fileId()).orElseThrow().status());

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertTrue(applied.applied());
        assertEquals(1, applied.repaired());
        assertEquals(FileObjectReconciler.Action.MARK_MISSING, applied.repairs().getFirst().action());
        assertEquals(FileStatus.MISSING, fixture.repo.find(ref.fileId()).orElseThrow().status());
        NexusException missing = assertThrows(NexusException.class, () -> fixture.service.metadata(ref.fileId()));
        assertEquals(ErrorCode.FILE_MISSING, missing.errorCode());
        assertEquals(1, fixture.storage.puts(), "repair must not invent object bytes");
    }

    @Test
    void applyDeletesOwnedOrphanAndLeavesForeignPrefix() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-orphan-a");
        Fixture fixture = fixture();
        FileRef owned = fixture.service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");
        String orphanA = "tenants/" + TENANT_A + "/ledger/2026/09/orphan-a";
        String orphanB = "tenants/" + TENANT_B + "/ledger/2026/09/orphan-b";
        fixture.storage.put("nexus-files", orphanA, "lost-a".getBytes(), "text/plain", -1);
        fixture.storage.put("nexus-files", orphanB, "lost-b".getBytes(), "text/plain", -1);
        int deletesBefore = fixture.storage.deletes();

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertEquals(1, preview.matched());
        assertEquals(1, preview.orphan());
        assertEquals(0, preview.repaired());
        assertTrue(fixture.storage.exists("nexus-files", orphanA));
        assertTrue(fixture.storage.exists("nexus-files", orphanB));

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(1, applied.repaired());
        assertEquals(FileObjectReconciler.Action.DELETE_ORPHAN, applied.repairs().getFirst().action());
        assertFalse(fixture.storage.exists("nexus-files", orphanA));
        assertTrue(fixture.storage.exists("nexus-files", orphanB), "tenant A must not delete tenant B prefix");
        assertTrue(fixture.storage.exists("nexus-files", owned.objectKey()));
        assertEquals(deletesBefore + 1, fixture.storage.deletes());
    }

    @Test
    void tenantBApplyDoesNotSeeTenantAOrphan() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-seed-a");
        Fixture fixture = fixture();
        String orphanA = "tenants/" + TENANT_A + "/ledger/2026/09/keep-a";
        fixture.storage.put("nexus-files", orphanA, "keep".getBytes(), "text/plain", -1);
        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-b");

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(0, applied.orphan());
        assertEquals(0, applied.repaired());
        assertTrue(fixture.storage.exists("nexus-files", orphanA));
    }

    @Test
    void leftoverDeletedObjectIsRemovedOnApply() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-left");
        Fixture fixture = fixture();
        FileRef ref = fixture.service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");
        fixture.repo.update(fixture.repo.find(ref.fileId()).orElseThrow().deleted());

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertEquals(1, preview.leftover());
        assertTrue(fixture.storage.exists("nexus-files", ref.objectKey()));

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(1, applied.repaired());
        assertEquals(FileObjectReconciler.Action.DELETE_LEFTOVER, applied.repairs().getFirst().action());
        assertFalse(fixture.storage.exists("nexus-files", ref.objectKey()));
    }

    @Test
    void foreignMetadataKeyIsConflictAndNotDeleted() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-conflict");
        Fixture fixture = fixture();
        String foreign = "tenants/" + TENANT_B + "/ledger/2026/09/stolen";
        fixture.storage.put("nexus-files", foreign, "b-bytes".getBytes(), "text/plain", -1);
        FileObject planted = new FileObject(
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                UUID.fromString(TENANT_A),
                "nexus-files",
                foreign,
                "stolen.txt",
                7,
                "text/plain",
                "00",
                FileStatus.UPLOADED,
                "user-a",
                "rec-x",
                Instant.parse("2026-09-22T00:00:00Z"),
                "user-a");
        fixture.repo.insert(planted);

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(1, applied.conflicts());
        assertEquals(0, applied.repaired());
        assertTrue(applied.findings().stream().anyMatch(item -> item.kind() == FileObjectReconciler.Kind.CONFLICT));
        assertTrue(fixture.storage.exists("nexus-files", foreign), "conflict must not delete foreign object");
    }

    @Test
    void platformApplyRepairsBothTenantsAndLeavesUnknownPrefix() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-plat-a");
        Fixture fixture = fixture();
        FileRef missing = fixture.service.upload("ledger", "a.txt", "text/plain", "aaa".getBytes(), "rec-a");
        fixture.storage.dropQuietly("nexus-files", missing.objectKey());
        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-plat-b");
        fixture.service.upload("ledger", "b.txt", "text/plain", "bbb".getBytes(), "rec-b");
        String orphanB = "tenants/" + TENANT_B + "/ledger/2026/09/orphan-b";
        String unknown = "tenants/" + UNKNOWN + "/ledger/2026/09/stray";
        fixture.storage.put("nexus-files", orphanB, "ob".getBytes(), "text/plain", -1);
        fixture.storage.put("nexus-files", unknown, "xx".getBytes(), "text/plain", -1);
        IsolationSupport.clear();
        IsolationSupport.bindPlatform(kernel, "files.reconcile", "ops-1", "file-reconcile", "trace-ops");

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertEquals(1, preview.missing());
        assertEquals(2, preview.orphan());
        assertEquals(0, preview.repaired());

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(2, applied.repaired());
        assertEquals(FileStatus.MISSING, fixture.repo.find(missing.fileId()).orElseThrow().status());
        assertFalse(fixture.storage.exists("nexus-files", orphanB));
        assertTrue(fixture.storage.exists("nexus-files", unknown), "unknown tenant prefix is report-only");
        assertTrue(applied.findings().stream().anyMatch(item ->
                item.kind() == FileObjectReconciler.Kind.ORPHAN && unknown.equals(item.objectKey())));
    }

    private Fixture fixture() {
        CountingObjectStorage storage = new CountingObjectStorage();
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (s, r) -> true, clock, "nexus-files", 1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileReconcileJob job = new FileReconcileJob(new FileObjectReconciler(storage, repo, "nexus-files"));
        return new Fixture(storage, repo, service, job);
    }

    private record Fixture(
            CountingObjectStorage storage,
            InMemoryFileObjectRepository repo,
            FileService service,
            FileReconcileJob job) {}
}
