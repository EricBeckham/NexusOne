package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.file.FileRef;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.file.infra.MinioObjectStorage;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Optional live MinIO isolation drill. Skips when {@code S3_ENDPOINT} (and
 * credentials / bucket) are missing or unreachable. Proves {@code P1.M02.015}
 * reject-before-execute with a guard record. Does not require Docker.
 */
class LiveMinioIsolationDrillTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    private MinioClient probe;
    private String bucket;
    private final List<String> cleanupKeys = new ArrayList<>();

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
        if (probe != null && bucket != null) {
            for (String key : cleanupKeys) {
                try {
                    probe.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
                } catch (Exception ignored) {
                    // best-effort cleanup of this drill's objects
                }
            }
        }
    }

    @Test
    void crossTenantSignIsRejectedBeforeLiveMinioAndRecordsGuard() {
        LiveMinioSession session = connectOrSkip();
        probe = session.client();
        bucket = session.bucket();

        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of("files.reconcile"), sink);
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-live-minio-a");

        DelegatingCountingObjectStorage storage = new DelegatingCountingObjectStorage(session.storage());
        FileService service = new FileService(
                storage,
                new InMemoryFileObjectRepository(),
                (subject, resource) -> true,
                Clock.systemUTC(),
                bucket,
                1024,
                Duration.ofSeconds(60),
                Set.of("text/plain"));

        FileRef ref = service.upload("ledger", "g1-live.txt", "text/plain", "hello-live".getBytes(), "record-live");
        cleanupKeys.add(ref.objectKey());
        assertTrue(ref.objectKey().startsWith("tenants/" + TENANT_A + "/"));
        assertEquals(1, storage.puts());
        assertEquals("hello-live", new String(session.storage().get(bucket, ref.objectKey())));
        int putsAfterUpload = storage.puts();
        int signsAfterUpload = storage.signs();
        int getsAfterUpload = storage.gets();

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-live-minio-b");
        IsolationException sign = assertThrows(IsolationException.class, () ->
                service.signDownload(ref.fileId(), "user-a"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, sign.error());
        assertEquals("P1.M02.015", sign.code());

        IsolationException download = assertThrows(IsolationException.class, () ->
                service.download(ref.fileId()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, download.error());

        IsolationException prefix = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), ref.objectKey()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, prefix.error());

        assertEquals(putsAfterUpload, storage.puts(), "reject must not put to MinIO");
        assertEquals(signsAfterUpload, storage.signs(), "reject must not presign");
        assertEquals(getsAfterUpload, storage.gets(), "reject must not get");
        String relative = ref.objectKey().substring(("tenants/" + TENANT_A + "/").length());
        assertFalse(hasObject(session.client(), bucket, "tenants/" + TENANT_B + "/" + relative));
        assertTrue(hasObject(session.client(), bucket, ref.objectKey()));
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        assertTrue(sink.rejectCount() >= 3);
        System.out.println("LIVE_MINIO_ISOLATION_DRILL endpoint=" + session.endpoint()
                + " bucket=" + bucket + " object=" + ref.objectKey());
    }

    private static LiveMinioSession connectOrSkip() {
        String endpoint = env("S3_ENDPOINT");
        String accessKey = env("S3_ACCESS_KEY");
        String secretKey = env("S3_SECRET_KEY");
        String bucket = System.getenv().getOrDefault("S3_TEST_BUCKET", "nexus-g1-live");
        assumeTrue(notBlank(endpoint) && notBlank(accessKey) && notBlank(secretKey),
                "Live MinIO skipped: set S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY (optional S3_TEST_BUCKET)");
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            assumeTrue(exists, "Live MinIO skipped: bucket " + bucket + " is not present");
            return new LiveMinioSession(endpoint, bucket, client, new MinioObjectStorage(endpoint, accessKey, secretKey));
        } catch (Exception ex) {
            assumeTrue(false, "Live MinIO not reachable: " + ex.getMessage());
            throw new IllegalStateException(ex);
        }
    }

    private static boolean hasObject(MinioClient client, String bucket, String objectKey) {
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(objectKey)
                    .recursive(true)
                    .build())) {
                if (objectKey.equals(result.get().objectName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalStateException("list object failed", ex);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record LiveMinioSession(
            String endpoint, String bucket, MinioClient client, MinioObjectStorage storage) {}
}
