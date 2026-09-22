package one.nexus.cache;

import java.util.List;

/**
 * P0-05A capability matrix for product Redis <strong>3.2</strong>.
 * The P0-05 compose asset used Redis 8 as a candidate; this adapter only emits 3.2 commands.
 */
public final class Redis32CommandMatrix {
    private Redis32CommandMatrix() {}

    public static final List<String> SUPPORTED = List.of(
            "PING",
            "GET",
            "SET key value EX seconds",
            "SET key value NX PX milliseconds",
            "SETEX",
            "SETNX",
            "DEL",
            "EXISTS",
            "TTL / PTTL / EXPIRE / PEXPIRE",
            "INCR / INCRBY / DECR / DECRBY",
            "EVAL / EVALSHA (Lua 5.1)",
            "MGET / MSET"
    );

    /**
     * Commands the product must not rely on against Redis 3.2.
     * Later Redis versions may accept them; the adapter still does not emit them.
     */
    public static final List<String> UNSUPPORTED = List.of(
            "UNLINK (Redis 4.0)",
            "MEMORY * (Redis 4.0)",
            "SWAPDB / REPLICAOF (Redis 4.0)",
            "Modules API (Redis 4.0)",
            "STREAMS / XADD / XREAD (Redis 5.0)",
            "ACL / AUTH ACL users (Redis 6.0)",
            "native TLS (Redis 6.0)",
            "HELLO / CLIENT TRACKING (Redis 6.0)",
            "SET KEEPTTL | GET | EXAT | PXAT (Redis 6.2)",
            "GETEX / GETDEL (Redis 6.2)",
            "COPY (Redis 6.2)",
            "FUNCTION LOAD / FCALL (Redis 7.0)",
            "SPUBLISH / sharded pubsub (Redis 7.0)",
            "HEXPIRE (Redis 7.4)",
            "JSON.SET / RedisJSON module",
            "FT.SEARCH / RediSearch"
    );

    public static String summary() {
        return "Redis 3.2 CachePort uses GET/SET EX/SET NX PX/DEL/INCR/EVAL only. "
                + "Unsupported: " + String.join("; ", UNSUPPORTED);
    }
}
