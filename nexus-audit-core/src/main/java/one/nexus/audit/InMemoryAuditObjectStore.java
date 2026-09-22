package one.nexus.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-process Fake for cold-archive unit tests (same put/get/list counters as CountingObjectStorage). */
public final class InMemoryAuditObjectStore implements AuditObjectStore {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicInteger puts = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger exists = new AtomicInteger();
    private final AtomicInteger lists = new AtomicInteger();

    @Override
    public void put(String objectKey, byte[] body) {
        puts.incrementAndGet();
        objects.put(objectKey, body.clone());
    }

    @Override
    public byte[] get(String objectKey) {
        gets.incrementAndGet();
        byte[] body = objects.get(objectKey);
        return body == null ? new byte[0] : body.clone();
    }

    @Override
    public boolean exists(String objectKey) {
        exists.incrementAndGet();
        return objects.containsKey(objectKey);
    }

    @Override
    public List<String> list(String prefix) {
        lists.incrementAndGet();
        String wanted = prefix == null ? "" : prefix;
        List<String> keys = new ArrayList<>();
        for (String key : objects.keySet()) {
            if (key.startsWith(wanted)) {
                keys.add(key);
            }
        }
        keys.sort(String::compareTo);
        return List.copyOf(keys);
    }

    public int puts() {
        return puts.get();
    }

    public int gets() {
        return gets.get();
    }

    public int existsCalls() {
        return exists.get();
    }

    public int lists() {
        return lists.get();
    }
}
