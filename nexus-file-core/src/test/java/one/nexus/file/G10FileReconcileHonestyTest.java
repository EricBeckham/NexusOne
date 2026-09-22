package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.file.FileRef;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G10 leftover honesty: counting preview vs apply + tenant-prefix guards
 * always-green; live MinIO / object-store is honest SKIP when unavailable.
 * Does not redo identity drift (#141) or G6 object restore. Does not claim
 * product G10 close.
 */
class G10FileReconcileHonestyTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("files.reconcile"), sink);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void countingPreviewLeavesMissingAndApplyMarksMetadataOnly() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-g10-honesty-preview");
        Fixture fixture = fixture();
        FileRef ref = fixture.service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "rec-1");
        fixture.storage.dropQuietly("nexus-files", ref.objectKey());

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertFalse(preview.applied());
        assertEquals("files.reconcile", preview.path());
        assertEquals(1, preview.missing());
        assertEquals(0, preview.repaired());
        assertEquals(FileStatus.UPLOADED, fixture.repo.find(ref.fileId()).orElseThrow().status());
        assertEquals(1, fixture.storage.puts(), "preview must not invent object bytes");

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertTrue(applied.applied());
        assertEquals(1, applied.repaired());
        assertEquals(FileObjectReconciler.Action.MARK_MISSING, applied.repairs().getFirst().action());
        assertEquals(FileStatus.MISSING, fixture.repo.find(ref.fileId()).orElseThrow().status());
        assertFalse(fixture.storage.exists("nexus-files", ref.objectKey()), "not G6 restore");
        assertEquals(1, fixture.storage.puts(), "apply MARK_MISSING must not reconstruct bytes");
    }

    @Test
    void tenantPrefixGuardLeavesForeignOrphanOnApply() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-g10-honesty-prefix");
        Fixture fixture = fixture();
        String orphanA = "tenants/" + TENANT_A + "/ledger/2026/09/honesty-a";
        String orphanB = "tenants/" + TENANT_B + "/ledger/2026/09/honesty-b";
        fixture.storage.put("nexus-files", orphanA, "a".getBytes(), "text/plain", -1);
        fixture.storage.put("nexus-files", orphanB, "b".getBytes(), "text/plain", -1);

        FileObjectReconciler.Report preview = fixture.job.run(false);
        assertEquals(1, preview.orphan());
        assertEquals(0, preview.repaired());
        assertTrue(fixture.storage.exists("nexus-files", orphanA));
        assertTrue(fixture.storage.exists("nexus-files", orphanB));

        FileObjectReconciler.Report applied = fixture.job.run(true);
        assertEquals(1, applied.repaired());
        assertEquals(FileObjectReconciler.Action.DELETE_ORPHAN, applied.repairs().getFirst().action());
        assertFalse(fixture.storage.exists("nexus-files", orphanA));
        assertTrue(fixture.storage.exists("nexus-files", orphanB), "tenant A must not delete tenant B prefix");
    }

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        G10FileReconcileHonesty.Inventory inventory = G10FileReconcileHonesty.probeLive();
        assertEquals("SKIP", inventory.liveReconcile());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.endpointSet() && inventory.credentialsSet() && inventory.listenerPresent());
        }

        Map<String, String> report = G10FileReconcileHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G10FileReconcileHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g10_close"));
        assertEquals("false", report.get("identity_drift_141_redone"));
        assertEquals("false", report.get("g6_object_restore_redone"));
        assertEquals("PASS", report.get("counting_preview_apply"));
        assertEquals("PASS", report.get("tenant_prefix_guard"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G10FileReconcileHonesty.verify(G10FileReconcileHonesty.parseReport(fixtureFile("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveMinioPass() {
        Map<String, String> fake = new HashMap<>(G10FileReconcileHonesty.parseReport(
                fixtureFile("fake-live-minio-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G10FileReconcileHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_minio"));
    }

    @Test
    void honestyGateRejectsG10CloseClaim() {
        Map<String, String> fake = new HashMap<>(G10FileReconcileHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("g10_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G10FileReconcileHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g10_close"));
    }

    @Test
    void honestyGateRejectsIdentityDriftAndG6Redo() {
        Map<String, String> fake = new HashMap<>(G10FileReconcileHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("identity_drift_141_redone", "true");
        IllegalArgumentException identity = assertThrows(IllegalArgumentException.class,
                () -> G10FileReconcileHonesty.verify(fake));
        assertTrue(identity.getMessage().contains("identity_drift_141_redone"));

        fake.put("identity_drift_141_redone", "false");
        fake.put("g6_object_restore_redone", "true");
        IllegalArgumentException restore = assertThrows(IllegalArgumentException.class,
                () -> G10FileReconcileHonesty.verify(fake));
        assertTrue(restore.getMessage().contains("g6_object_restore_redone"));
    }

    private Fixture fixture() {
        CountingObjectStorage storage = new CountingObjectStorage();
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (s, r) -> true, CLOCK, "nexus-files", 1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileReconcileJob job = new FileReconcileJob(new FileObjectReconciler(storage, repo, "nexus-files"));
        return new Fixture(storage, repo, service, job);
    }

    private static Path fixtureFile(String name) {
        Path first = Path.of("scripts/g10-file-reconcile-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g10-file-reconcile-honesty/fixtures/" + name);
    }

    private record Fixture(
            CountingObjectStorage storage,
            InMemoryFileObjectRepository repo,
            FileService service,
            FileReconcileJob job) {}
}
