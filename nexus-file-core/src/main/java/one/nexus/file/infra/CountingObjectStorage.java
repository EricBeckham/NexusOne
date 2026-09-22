package one.nexus.file.infra;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.file.ObjectStoragePort;

/** In-memory S3 stand-in that counts put/get/presign/delete for isolation tests. */
public final class CountingObjectStorage implements ObjectStoragePort {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicInteger puts = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger signs = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();

    @Override
    public void put(String bucket, String objectKey, byte[] body, String contentType, long partSize) {
        puts.incrementAndGet();
        objects.put(bucket + "/" + objectKey, body.clone());
    }

    @Override
    public byte[] get(String bucket, String objectKey) {
        gets.incrementAndGet();
        byte[] body = objects.get(bucket + "/" + objectKey);
        return body == null ? new byte[0] : body.clone();
    }

    @Override
    public String presignGet(String bucket, String objectKey, Duration ttl) {
        signs.incrementAndGet();
        return "https://signed.example/" + objectKey + "?ttl=" + ttl.toSeconds();
    }

    @Override
    public void delete(String bucket, String objectKey) {
        deletes.incrementAndGet();
        objects.remove(bucket + "/" + objectKey);
    }

    public int puts() {
        return puts.get();
    }

    public int gets() {
        return gets.get();
    }

    public int signs() {
        return signs.get();
    }

    public int deletes() {
        return deletes.get();
    }
}
