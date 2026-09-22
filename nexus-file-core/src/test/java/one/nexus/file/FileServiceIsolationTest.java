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
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FileServiceIsolationTest {
    private static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_B = "22222222-2222-4222-8222-222222222222";

    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("files.reconcile"), new CollectingAuditSink());
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void uploadUsesTenantPrefixAndFileId() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-a");
        CountingObjectStorage storage = new CountingObjectStorage();
        FileService service = service(storage, (subject, resource) -> true);
        FileRef ref = service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "record-1");
        assertEquals(UUID.fromString(TENANT_A), ref.tenantId());
        assertTrue(ref.objectKey().startsWith("tenants/" + TENANT_A + "/ledger/2026/09/"));
        assertEquals(ref.objectKey(), FileObjectKeyGuard.objectKey(AccessContextHolder.current(),
                "ledger/2026/09/" + ref.fileId()));
        assertEquals(1, storage.puts());
        SignedUrl signed = service.signDownload(ref.fileId(), "user-a");
        assertTrue(signed.url().contains(ref.objectKey()));
        assertEquals(1, storage.signs());
    }

    @Test
    void crossTenantSignIsRejectedBeforeS3() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-a");
        CountingObjectStorage storage = new CountingObjectStorage();
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (s, r) -> true, clock, "nexus-files", 1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileRef ref = service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "record-1");
        IsolationSupport.clear();
        IsolationSupport.bindTenant(kernel, TENANT_B, "user-b", "mem-b", "trace-b");
        IsolationException ex = assertThrows(IsolationException.class, () ->
                service.signDownload(ref.fileId(), "user-a"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, ex.error());
        assertEquals(0, storage.signs());
        IsolationException prefix = assertThrows(IsolationException.class, () ->
                FileObjectKeyGuard.assertOwned(AccessContextHolder.current(), ref.objectKey()));
        assertEquals(IsolationErrorCode.CROSS_TENANT_FILE, prefix.error());
        assertEquals(0, storage.signs());
    }

    @Test
    void ownerAndPermissionAreRecheckedBeforePresign() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-a");
        CountingObjectStorage storage = new CountingObjectStorage();
        FileService deny = service(storage, (subject, resource) -> false);
        FileRef ref = deny.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "record-1");
        NexusException ownership = assertThrows(NexusException.class, () ->
                deny.signDownload(ref.fileId(), "user-other"));
        assertEquals(ErrorCode.FILE_DENIED, ownership.errorCode());
        NexusException permission = assertThrows(NexusException.class, () ->
                deny.signDownload(ref.fileId(), "user-a"));
        assertEquals(ErrorCode.FILE_DENIED, permission.errorCode());
        assertEquals(0, storage.signs());
    }

    @Test
    void pathTraversalBusinessTypeIsRejectedBeforeStorage() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-a");
        CountingObjectStorage storage = new CountingObjectStorage();
        FileService service = service(storage, (s, r) -> true);
        NexusException ex = assertThrows(NexusException.class, () ->
                service.upload("../beta", "x", "text/plain", "x".getBytes(), "r"));
        assertEquals(ErrorCode.FILE_INVALID, ex.errorCode());
        assertEquals(0, storage.puts());
    }

    @Test
    void platformContextMayReadTenantPrefixWithoutPresignBypass() {
        IsolationSupport.bindTenant(kernel, TENANT_A, "user-a", "mem-a", "trace-a");
        CountingObjectStorage storage = new CountingObjectStorage();
        InMemoryFileObjectRepository repo = new InMemoryFileObjectRepository();
        FileService service = new FileService(
                storage, repo, (s, r) -> true, clock, "nexus-files", 1024, Duration.ofSeconds(60), Set.of("text/plain"));
        FileRef ref = service.upload("ledger", "doc.txt", "text/plain", "hello".getBytes(), "record-1");
        IsolationSupport.clear();
        IsolationSupport.bindPlatform(kernel, "files.reconcile", "ops-1", "reconcile", "trace-ops");
        FileObject meta = service.metadata(ref.fileId());
        assertEquals(ref.fileId(), meta.fileId());
        SignedUrl signed = service.signDownload(ref.fileId(), "ops-1");
        assertTrue(signed.url().contains(ref.objectKey()));
        assertEquals(1, storage.signs());
        assertFalse(ref.objectKey().startsWith("platform/"));
    }

    private FileService service(CountingObjectStorage storage, FileAccessPolicy policy) {
        return new FileService(
                storage,
                new InMemoryFileObjectRepository(),
                policy,
                clock,
                "nexus-files",
                1024,
                Duration.ofSeconds(60),
                Set.of("text/plain"));
    }
}
