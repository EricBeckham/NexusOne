package one.nexus.file;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import one.nexus.common.file.FileRef;

public record FileObject(
        UUID fileId,
        UUID tenantId,
        String bucketAlias,
        String objectKey,
        String originalName,
        long sizeBytes,
        String contentType,
        String checksumSha256,
        FileStatus status,
        String ownerSubject,
        String resourceId,
        Instant createdAt,
        String createdBy) {
    public FileObject {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (bucketAlias == null || bucketAlias.isBlank()) {
            throw new IllegalArgumentException("bucketAlias is required");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
        Objects.requireNonNull(status, "status");
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("ownerSubject is required");
        }
    }

    public FileRef ref() {
        return new FileRef(fileId, tenantId, objectKey);
    }

    public FileObject deleted() {
        return new FileObject(
                fileId, tenantId, bucketAlias, objectKey, originalName, sizeBytes, contentType,
                checksumSha256, FileStatus.DELETED, ownerSubject, resourceId, createdAt, createdBy);
    }
}
