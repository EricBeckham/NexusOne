package one.nexus.cache;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts Redis 3.2 commands so live isolation drills can prove a reject never
 * reached the backend.
 */
final class CountingRedis32Commands implements Redis32Commands {
    private final Redis32Commands delegate;
    private final AtomicInteger commands = new AtomicInteger();

    CountingRedis32Commands(Redis32Commands delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String ping() {
        commands.incrementAndGet();
        return delegate.ping();
    }

    @Override
    public String get(String key) {
        commands.incrementAndGet();
        return delegate.get(key);
    }

    @Override
    public String setEx(String key, long seconds, String value) {
        commands.incrementAndGet();
        return delegate.setEx(key, seconds, value);
    }

    @Override
    public String setNxPx(String key, String value, long ttlMs) {
        commands.incrementAndGet();
        return delegate.setNxPx(key, value, ttlMs);
    }

    @Override
    public long del(String key) {
        commands.incrementAndGet();
        return delegate.del(key);
    }

    @Override
    public long incr(String key) {
        commands.incrementAndGet();
        return delegate.incr(key);
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        commands.incrementAndGet();
        return delegate.eval(script, keys, args);
    }

    @Override
    public String infoServer() {
        commands.incrementAndGet();
        return delegate.infoServer();
    }

    int commandCount() {
        return commands.get();
    }
}
