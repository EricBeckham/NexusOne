package one.nexus.cache;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts Redis 3.2-safe commands so isolation tests can prove a reject never
 * reached the live or fake backend.
 */
final class CountingRedis32Commands implements Redis32Commands {
    private final Redis32Commands delegate;
    private final AtomicInteger commands = new AtomicInteger();
    private final List<String> ops = new CopyOnWriteArrayList<>();

    CountingRedis32Commands(Redis32Commands delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    int commandCount() {
        return commands.get();
    }

    List<String> ops() {
        return List.copyOf(ops);
    }

    private void note(String op) {
        commands.incrementAndGet();
        ops.add(op);
    }

    @Override
    public String ping() {
        note("PING");
        return delegate.ping();
    }

    @Override
    public String get(String key) {
        note("GET");
        return delegate.get(key);
    }

    @Override
    public String setEx(String key, long seconds, String value) {
        note("SETEX");
        return delegate.setEx(key, seconds, value);
    }

    @Override
    public String setNxPx(String key, String value, long ttlMs) {
        note("SETNX_PX");
        return delegate.setNxPx(key, value, ttlMs);
    }

    @Override
    public long del(String key) {
        note("DEL");
        return delegate.del(key);
    }

    @Override
    public long incr(String key) {
        note("INCR");
        return delegate.incr(key);
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        note("EVAL");
        return delegate.eval(script, keys, args);
    }

    @Override
    public String infoServer() {
        note("INFO");
        return delegate.infoServer();
    }
}
