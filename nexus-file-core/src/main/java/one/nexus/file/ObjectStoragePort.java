package one.nexus.file;

import java.time.Duration;
import java.util.List;

/**
 * S3 adapter. FileService must reject cross-tenant work before invoking these methods.
 * {@link #listKeys} is prefix-scoped; callers must pass a tenant-owned prefix.
 */
public interface ObjectStoragePort {
    void put(String bucket, String objectKey, byte[] body, String contentType, long partSize);

    byte[] get(String bucket, String objectKey);

    String presignGet(String bucket, String objectKey, Duration ttl);

    void delete(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);

    List<String> listKeys(String bucket, String prefix);
}
