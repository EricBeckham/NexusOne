package one.nexus.cache;

import java.util.List;

/**
 * Redis 3.2 command surface used by CachePort. Adapters must not call commands
 * outside this set (see {@link Redis32CommandMatrix}).
 */
public interface Redis32Commands {
    String ping();

    String get(String key);

    /** {@code SET key value EX seconds} — present since Redis 2.6.12. */
    String setEx(String key, long seconds, String value);

    /** {@code SET key value NX PX ms} — present since Redis 2.6.12. */
    String setNxPx(String key, String value, long ttlMs);

    long del(String key);

    long incr(String key);

    Object eval(String script, List<String> keys, List<String> args);

    String infoServer();
}
