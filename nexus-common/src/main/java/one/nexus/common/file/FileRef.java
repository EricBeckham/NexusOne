package one.nexus.common.file;

import java.util.Objects;
import java.util.UUID;

/**
 * File reference. Object bytes live in S3; metadata is M0-10.
 */
public record FileRef(UUID fileId, UUID tenantId, String objectKey) {
    public FileRef {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
    }
}
