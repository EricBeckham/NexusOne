package one.nexus.file;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts ObjectStoragePort calls so a live MinIO isolation drill can prove
 * cross-tenant rejects never reach put/get/presign/delete.
 */
final class InstrumentedObjectStorage implements ObjectStoragePort {
    private final ObjectStoragePort delegate;
    private final AtomicInteger puts = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger signs = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();

    InstrumentedObjectStorage(ObjectStoragePort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    int puts() {
        return puts.get();
    }

    int gets() {
        return gets.get();
    }

    int signs() {
        return signs.get();
    }

    int deletes() {
        return deletes.get();
    }

    @Override
    public void put(String bucket, String objectKey, byte[] body, String contentType, long partSize) {
        puts.incrementAndGet();
        delegate.put(bucket, objectKey, body, contentType, partSize);
    }

    @Override
    public byte[] get(String bucket, String objectKey) {
        gets.incrementAndGet();
        return delegate.get(bucket, objectKey);
    }

    @Override
    public String presignGet(String bucket, String objectKey, Duration ttl) {
        signs.incrementAndGet();
        return delegate.presignGet(bucket, objectKey, ttl);
    }

    @Override
    public void delete(String bucket, String objectKey) {
        deletes.incrementAndGet();
        delegate.delete(bucket, objectKey);
    }
}
