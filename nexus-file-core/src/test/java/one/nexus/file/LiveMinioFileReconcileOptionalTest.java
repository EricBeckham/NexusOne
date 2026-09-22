package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.file.FileRef;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.file.infra.MinioObjectStorage;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Optional live MinIO G10 file reconcile. Prefers dedicated {@code S3_ENDPOINT}
 * (ops default http://127.0.0.1:19000). Skips when endpoint/credentials are
 * missing or the bucket cannot be reached. File metadata stays in-memory so
 * this drill does not bind product PostgreSQL {@code :5432}. Not G6 restore.
 */
class LiveMinioFileReconcileOptionalTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    private MinioClient client;
    private String bucket;
    private final List<String> cleanupKeys = new ArrayList<>();

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
        if (client != null && bucket != null) {
            for (String key : cleanupKeys) {
                try {
                    client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
                } catch (Exception ignored) {
                    // best-effort cleanup of this drill's objects
                }
            }
        }
    }

    @Test
    void liveMinioReportsMissingAndDeletesOwnedOrphanOnly() throws Exception {
        String endpoint = System.getenv("S3_ENDPOINT");
        String access = System.getenv("S3_ACCESS_KEY");
        String secret = System.getenv("S3_SECRET_KEY");
        bucket = System.getenv().getOrDefault("S3_TEST_BUCKET", "nexus-isolation-drill");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "SKIP live MinIO: S3_ENDPOINT unset");
        assumeTrue(access != null && !access.isBlank(), "SKIP live MinIO: S3_ACCESS_KEY unset");
        assumeTrue(secret != null && !secret.isBlank(), "SKIP live MinIO: S3_SECRET_KEY unset");

        try {
            client = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
            ensureBucket(client, bucket);
        } catch (Exception ex) {
            assumeTrue(false, "live MinIO not reachable at " + endpoint + ": " + ex.getMessage());
            return;
        }

        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("files.reconcile"), sink);
        MinioObjectStorage storage = new MinioObjectStorage(endpoint, access, secret);
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (subject, resource) -> true, Clock.systemUTC(), bucket,
                1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileReconcileJob job = new FileReconcileJob(new FileObjectReconciler(storage, repo, bucket));

        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-g10-live-a");
        FileRef missing = service.upload("ledger", "gone.txt", "text/plain", "keep-meta".getBytes(), "rec-a");
        cleanupKeys.add(missing.objectKey());
        storage.delete(bucket, missing.objectKey());

        String orphanA = "tenants/" + TENANT_A + "/ledger/2026/09/" + UUID.randomUUID();
        String orphanB = "tenants/" + TENANT_B + "/ledger/2026/09/" + UUID.randomUUID();
        storage.put(bucket, orphanA, "orphan-a".getBytes(), "text/plain", -1);
        storage.put(bucket, orphanB, "orphan-b".getBytes(), "text/plain", -1);
        cleanupKeys.add(orphanA);
        cleanupKeys.add(orphanB);

        FileObjectReconciler.Report preview = job.run(false);
        assertEquals(1, preview.missing());
        assertEquals(1, preview.orphan());
        assertEquals(0, preview.repaired());
        assertTrue(storage.exists(bucket, orphanA));
        assertTrue(storage.exists(bucket, orphanB));

        FileObjectReconciler.Report applied = job.run(true);
        assertEquals(2, applied.repaired());
        assertEquals(FileStatus.MISSING, repo.find(missing.fileId()).orElseThrow().status());
        assertFalse(storage.exists(bucket, orphanA));
        assertTrue(storage.exists(bucket, orphanB), "live apply must not delete the other tenant prefix");
        assertFalse(storage.exists(bucket, missing.objectKey()), "repair must not reconstruct missing bytes");

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-g10-live-b");
        FileObjectReconciler.Report other = job.run(true);
        assertEquals(1, other.orphan());
        assertEquals(1, other.repaired());
        assertFalse(storage.exists(bucket, orphanB));

        System.out.println("LIVE_MINIO_FILE_RECONCILE endpoint=" + endpoint
                + " bucket=" + bucket
                + " missing=" + applied.missing()
                + " orphan_deleted=2");
    }

    private static void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
