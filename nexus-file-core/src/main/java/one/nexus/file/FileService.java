package one.nexus.file;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.file.FileRef;
import one.nexus.common.id.UuidV7;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.boundary.SideEffectGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Product File Service. Object keys are {@code tenants/{tenantId}/{type}/{yyyy}/{mm}/{fileId}}
 * via {@link FileObjectKeyGuard}. Signed URLs re-check tenant, prefix, owner, and permission
 * before the S3 adapter is invoked.
 */
public final class FileService {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<String> DEFAULT_MIME = Set.of(
            "application/octet-stream",
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "text/csv"
    );

    private final ObjectStoragePort storage;
    private final FileObjectRepository repository;
    private final FileAccessPolicy policy;
    private final Clock clock;
    private final String bucketAlias;
    private final long maxSizeBytes;
    private final Duration signedTtl;
    private final Set<String> allowedMime;

    public FileService(
            ObjectStoragePort storage,
            FileObjectRepository repository,
            FileAccessPolicy policy,
            Clock clock,
            String bucketAlias,
            long maxSizeBytes,
            Duration signedTtl,
            Set<String> allowedMime) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bucketAlias = requireSegment(bucketAlias, "bucket");
        this.maxSizeBytes = maxSizeBytes;
        this.signedTtl = Objects.requireNonNull(signedTtl, "signedTtl");
        this.allowedMime = Set.copyOf(allowedMime == null || allowedMime.isEmpty() ? DEFAULT_MIME : allowedMime);
    }

    public FileRef upload(String businessType, String originalName, String contentType, byte[] body, String resourceId) {
        AccessContext context = AccessContextHolder.current();
        byte[] bytes = Objects.requireNonNull(body, "body");
        if (bytes.length > maxSizeBytes) {
            throw new NexusException(ErrorCode.FILE_INVALID, 400, "file exceeds size limit");
        }
        String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        if (!allowedMime.contains(mime.toLowerCase(Locale.ROOT)) && !allowedMime.contains(mime)) {
            throw new NexusException(ErrorCode.FILE_INVALID, 400, "content type not allowed");
        }
        String type = requireSegment(businessType, "businessType");
        UUID fileId = UuidV7.generate();
        LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String relative = type + "/" + day.getYear() + "/" + String.format("%02d", day.getMonthValue()) + "/" + fileId;
        AtomicReference<String> storedKey = new AtomicReference<>();
        FileObjectKeyGuard.putObject(relative, bytes, key -> {
            storage.put(bucketAlias, key, bytes, mime, partSize(bytes.length));
            storedKey.set(key);
        });
        UUID tenantId = tenantId(context);
        String owner = context.actor().actorId();
        FileObject file = new FileObject(
                fileId,
                tenantId,
                bucketAlias,
                storedKey.get(),
                originalName == null || originalName.isBlank() ? fileId.toString() : originalName,
                bytes.length,
                mime,
                sha256(bytes),
                FileStatus.UPLOADED,
                owner,
                resourceId,
                Instant.now(clock),
                owner);
        repository.insert(file);
        return file.ref();
    }

    public FileObject metadata(UUID fileId) {
        FileObject file = requireFile(fileId);
        authorizeRead(file, AccessContextHolder.current().actor().actorId(), false);
        return file;
    }

    public byte[] download(UUID fileId) {
        FileObject file = requireFile(fileId);
        AccessContext context = AccessContextHolder.current();
        authorizeRead(file, context.actor().actorId(), false);
        FileObjectKeyGuard.assertOwned(context, file.objectKey());
        return storage.get(file.bucketAlias(), file.objectKey());
    }

    /**
     * Second authorization (P0-06 / tech framework §9.4): tenant, prefix, ownership, and
     * resource permission are checked again. S3 is not called on reject.
     */
    public SignedUrl signDownload(UUID fileId, String subject) {
        FileObject file = requireFile(fileId);
        authorizeRead(file, requireSegment(subject, "subject"), true);
        Instant expires = Instant.now(clock).plus(signedTtl);
        String url = storage.presignGet(file.bucketAlias(), file.objectKey(), signedTtl);
        return new SignedUrl(file.fileId(), url, expires);
    }

    public void delete(UUID fileId) {
        FileObject file = requireFile(fileId);
        AccessContext context = AccessContextHolder.current();
        SideEffectGuard.assertSameTenant(context, file.tenantId().toString());
        FileObjectKeyGuard.assertOwned(context, file.objectKey());
        if (context instanceof TenantContext && !policy.mayWrite(context.actor().actorId(), file.resourceId())) {
            throw new NexusException(ErrorCode.FILE_DENIED, 403, "write permission denied");
        }
        storage.delete(file.bucketAlias(), file.objectKey());
        repository.update(file.deleted());
    }

    private FileObject requireFile(UUID fileId) {
        FileObject file = repository.find(fileId)
                .orElseThrow(() -> new NexusException(ErrorCode.FILE_NOT_FOUND, 404, "file not found"));
        if (file.status() == FileStatus.DELETED) {
            throw new NexusException(ErrorCode.FILE_DELETED, 404, "file deleted");
        }
        if (file.status() == FileStatus.MISSING) {
            throw new NexusException(ErrorCode.FILE_MISSING, 404, "file object missing from store");
        }
        return file;
    }

    private void authorizeRead(FileObject file, String subject, boolean requireOwnerAndPermission) {
        AccessContext context = AccessContextHolder.current();
        FileObjectKeyGuard.assertOwned(context, file.objectKey());
        SideEffectGuard.assertSameTenant(context, file.tenantId().toString());
        if (context instanceof PlatformContext) {
            return;
        }
        if (!file.tenantId().toString().equals(boundTenantId(context))) {
            throw new NexusException(ErrorCode.FILE_DENIED, 403, "file tenant mismatch");
        }
        if (requireOwnerAndPermission) {
            if (!subject.equals(file.ownerSubject())) {
                throw new NexusException(ErrorCode.FILE_DENIED, 403, "file ownership mismatch");
            }
            if (!policy.mayRead(subject, file.resourceId())) {
                throw new NexusException(ErrorCode.FILE_DENIED, 403, "resource permission denied");
            }
        } else if (!subject.equals(file.ownerSubject()) && !policy.mayRead(subject, file.resourceId())) {
            throw new NexusException(ErrorCode.FILE_DENIED, 403, "file read denied");
        }
    }

    private static UUID tenantId(AccessContext context) {
        if (context instanceof TenantContext tenant) {
            return UUID.fromString(tenant.tenantId());
        }
        throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "tenant context required for upload");
    }

    private static String boundTenantId(AccessContext context) {
        return context.boundTenantId().orElseThrow(() ->
                new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required"));
    }

    static String requireSegment(String value, String name) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new NexusException(ErrorCode.FILE_INVALID, 400, "invalid " + name);
        }
        return value;
    }

    static String sha256(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long partSize(int length) {
        if (length > 5 * 1024 * 1024) {
            return 5L * 1024 * 1024;
        }
        return -1L;
    }
}
