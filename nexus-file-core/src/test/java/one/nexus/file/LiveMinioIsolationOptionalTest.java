package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
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
 * Optional live MinIO isolation drill. Prefers dedicated {@code S3_ENDPOINT}
 * (ops default http://127.0.0.1:19000). Skips when endpoint/credentials are
 * missing or the bucket cannot be reached. File metadata stays in-memory so
 * this drill does not bind product PostgreSQL {@code :5432}.
 */
class LiveMinioIsolationOptionalTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void foreignFilePrefixRejectedBeforeLiveMinioCall() throws Exception {
        String endpoint = System.getenv("S3_ENDPOINT");
        String access = System.getenv("S3_ACCESS_KEY");
        String secret = System.getenv("S3_SECRET_KEY");
        String bucket = System.getenv().getOrDefault("S3_TEST_BUCKET", "nexus-isolation-drill");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "SKIP live MinIO: S3_ENDPOINT unset");
        assumeTrue(access != null && !access.isBlank(), "SKIP live MinIO: S3_ACCESS_KEY unset");
        assumeTrue(secret != null && !secret.isBlank(), "SKIP live MinIO: S3_SECRET_KEY unset");

        MinioClient client;
        try {
            client = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
            ensureBucket(client, bucket);
        } catch (Exception ex) {
            assumeTrue(false, "live MinIO not reachable at " + endpoint + ": " + ex.getMessage());
            return;
        }

        CollectingAuditSink sink = new CollectingAuditSink();
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("files.reconcile"), sink);
        InstrumentedObjectStorage storage = new InstrumentedObjectStorage(new MinioObjectStorage(endpoint, access, secret));
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage,
                repo,
                (subject, resource) -> true,
                Clock.systemUTC(),
                bucket,
                1024,
                Duration.ofSeconds(60),
                Set.of("text/plain"));

        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-live-minio-a");
        FileRef ref = service.upload("ledger", "drill.txt", "text/plain", "tenant-a-bytes".getBytes(), "record-a");
        assertTrue(ref.objectKey().startsWith("tenants/" + TENANT_A + "/ledger/"));
        int putsAfterUpload = storage.puts();
        int signsAfterUpload = storage.signs();
        int deletesAfterUpload = storage.deletes();
        assertEquals(1, putsAfterUpload);

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-live-minio-b");
        IsolationException sign = assertThrows(IsolationException.class, () ->
                service.signDownload(ref.fileId(), "user-a"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, sign.error());
        IsolationException prefix = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), ref.objectKey()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, prefix.error());
        IsolationException delete = assertThrows(IsolationException.class, () -> service.delete(ref.fileId()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_ASYNC, delete.error());
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_FILE));
        assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_ASYNC));
        assertEquals(putsAfterUpload, storage.puts(), "P1.M02.015 must not PUT");
        assertEquals(signsAfterUpload, storage.signs(), "P1.M02.015 must not presign");
        assertEquals(deletesAfterUpload, storage.deletes(), "cross-tenant delete must not reach MinIO");

        List<String> tenantA = listKeys(client, bucket, "tenants/" + TENANT_A + "/");
        List<String> tenantB = listKeys(client, bucket, "tenants/" + TENANT_B + "/");
        assertTrue(tenantA.contains(ref.objectKey()), "tenant A object must remain after reject");
        assertFalse(tenantB.contains(ref.objectKey()), "tenant A object must not appear under B prefix");
        assertTrue(tenantB.isEmpty(), "cross-tenant reject must not create a B prefix object");

        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-live-minio-a2");
        assertEquals("tenant-a-bytes", new String(service.download(ref.fileId())));
        try {
            service.delete(ref.fileId());
        } finally {
            System.out.println("LIVE minio isolation PASS endpoint=" + endpoint
                    + " bucket=" + bucket
                    + " reject=P1.M02.015 puts=" + storage.puts()
                    + " signs=" + storage.signs()
                    + " deletes=" + storage.deletes());
        }
    }

    private static void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static List<String> listKeys(MinioClient client, String bucket, String prefix) throws Exception {
        List<String> keys = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build())) {
            keys.add(result.get().objectName());
        }
        return keys;
    }
}
