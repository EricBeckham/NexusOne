package one.nexus.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process Redis 3.2 command fake for unit tests. Counts invocations so
 * cross-tenant rejects can prove no command was sent.
 */
public final class FakeRedis32Commands implements Redis32Commands {
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final AtomicInteger commands = new AtomicInteger();

    @Override
    public String ping() {
        commands.incrementAndGet();
        return "PONG";
    }

    @Override
    public String get(String key) {
        commands.incrementAndGet();
        return values.get(key);
    }

    @Override
    public String setEx(String key, long seconds, String value) {
        commands.incrementAndGet();
        values.put(key, value);
        return "OK";
    }

    @Override
    public String setNxPx(String key, String value, long ttlMs) {
        commands.incrementAndGet();
        if (values.putIfAbsent(key, value) == null) {
            return "OK";
        }
        return null;
    }

    @Override
    public long del(String key) {
        commands.incrementAndGet();
        return values.remove(key) == null ? 0L : 1L;
    }

    @Override
    public long incr(String key) {
        commands.incrementAndGet();
        return incrementValue(key);
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        commands.incrementAndGet();
        String key = keys.get(0);
        if (script.contains("INCR")) {
            return incrementValue(key);
        }
        String expected = args.get(0);
        String current = values.get(key);
        if (expected.equals(current)) {
            values.remove(key);
            return 1L;
        }
        return 0L;
    }

    @Override
    public String infoServer() {
        commands.incrementAndGet();
        return "redis_version:3.2.0\r\n";
    }

    public int commandCount() {
        return commands.get();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    private long incrementValue(String key) {
        String next = values.compute(key, (ignored, current) -> {
            long value = current == null ? 1L : Long.parseLong(current) + 1L;
            return Long.toString(value);
        });
        return Long.parseLong(next);
    }
}
