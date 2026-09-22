package one.nexus.cache;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.SetParams;

/** Jedis mapping that only exposes Redis 3.2-safe commands. */
public final class JedisRedis32Commands implements Redis32Commands {
    private final JedisPooled jedis;

    public JedisRedis32Commands(JedisPooled jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
    }

    @Override
    public String ping() {
        return jedis.ping();
    }

    @Override
    public String get(String key) {
        return jedis.get(key);
    }

    @Override
    public String setEx(String key, long seconds, String value) {
        return jedis.setex(key, seconds, value);
    }

    @Override
    public String setNxPx(String key, String value, long ttlMs) {
        return jedis.set(key, value, new SetParams().nx().px(ttlMs));
    }

    @Override
    public long del(String key) {
        return jedis.del(key);
    }

    @Override
    public long incr(String key) {
        return jedis.incr(key);
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        return jedis.eval(script, keys, args);
    }

    @Override
    public String infoServer() {
        Object result = jedis.sendCommand(Protocol.Command.INFO, "server");
        if (result instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(result);
    }

    public void close() {
        jedis.close();
    }
}
