package one.nexus.file;

import java.time.Duration;

/**
 * S3 adapter. FileService must reject cross-tenant work before invoking these methods.
 */
public interface ObjectStoragePort {
    void put(String bucket, String objectKey, byte[] body, String contentType, long partSize);

    byte[] get(String bucket, String objectKey);

    String presignGet(String bucket, String objectKey, Duration ttl);

    void delete(String bucket, String objectKey);
}
