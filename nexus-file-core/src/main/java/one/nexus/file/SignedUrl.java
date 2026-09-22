package one.nexus.file;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SignedUrl(UUID fileId, String url, Instant expiresAt) {
    public SignedUrl {
        Objects.requireNonNull(fileId, "fileId");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
