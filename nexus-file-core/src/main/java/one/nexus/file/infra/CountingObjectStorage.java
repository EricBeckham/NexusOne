package one.nexus.file.infra;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.file.ObjectStoragePort;

/** In-memory S3 stand-in that counts put/get/presign/delete/list for isolation and reconcile tests. */
public final class CountingObjectStorage implements ObjectStoragePort {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicInteger puts = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger signs = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();
    private final AtomicInteger exists = new AtomicInteger();
    private final AtomicInteger lists = new AtomicInteger();

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

    @Override
    public boolean exists(String bucket, String objectKey) {
        exists.incrementAndGet();
        return objects.containsKey(bucket + "/" + objectKey);
    }

    @Override
    public List<String> listKeys(String bucket, String prefix) {
        lists.incrementAndGet();
        String head = bucket + "/";
        String wanted = head + (prefix == null ? "" : prefix);
        List<String> keys = new ArrayList<>();
        for (String stored : objects.keySet()) {
            if (stored.startsWith(wanted)) {
                keys.add(stored.substring(head.length()));
            }
        }
        keys.sort(String::compareTo);
        return List.copyOf(keys);
    }

    /** Drop an object without counting a delete — simulates lost store bytes. */
    public void dropQuietly(String bucket, String objectKey) {
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

    public int existsCalls() {
        return exists.get();
    }

    public int lists() {
        return lists.get();
    }
}
