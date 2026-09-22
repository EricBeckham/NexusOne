package one.nexus.audit;

import java.util.List;
import java.util.Objects;

/**
 * Adapts File {@code ObjectStoragePort} (MinIO or CountingObjectStorage Fake)
 * without a compile-time dependency on {@code nexus-file-core}. Product and
 * tests pass the port method references.
 */
public final class ObjectStorageAuditStore implements AuditObjectStore {
    @FunctionalInterface
    public interface Put {
        void put(String bucket, String objectKey, byte[] body, String contentType, long partSize);
    }

    @FunctionalInterface
    public interface Get {
        byte[] get(String bucket, String objectKey);
    }

    @FunctionalInterface
    public interface Exists {
        boolean exists(String bucket, String objectKey);
    }

    @FunctionalInterface
    public interface ListKeys {
        List<String> listKeys(String bucket, String prefix);
    }

    private final String bucket;
    private final Put put;
    private final Get get;
    private final Exists exists;
    private final ListKeys listKeys;

    public ObjectStorageAuditStore(String bucket, Put put, Get get, Exists exists, ListKeys listKeys) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        this.bucket = bucket;
        this.put = Objects.requireNonNull(put, "put");
        this.get = Objects.requireNonNull(get, "get");
        this.exists = Objects.requireNonNull(exists, "exists");
        this.listKeys = Objects.requireNonNull(listKeys, "listKeys");
    }

    @Override
    public void put(String objectKey, byte[] body) {
        put.put(bucket, objectKey, body, "application/json", -1);
    }

    @Override
    public byte[] get(String objectKey) {
        return get.get(bucket, objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return exists.exists(bucket, objectKey);
    }

    @Override
    public List<String> list(String prefix) {
        return listKeys.listKeys(bucket, prefix);
    }

    public String bucket() {
        return bucket;
    }
}
