package one.nexus.permission.redis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Minimal RESP2 client for Redis 3.2.x. Does not send HELLO / RESP3. Compatible
 * with the P0-05 command set (GET/SET EX/DEL/PUBLISH/PING). Does not claim
 * Sentinel, Cluster, or failover.
 */
public final class Redis32Client implements AutoCloseable {
    private final String host;
    private final int port;
    private final Duration timeout;

    public Redis32Client(String host, int port) {
        this(host, port, Duration.ofSeconds(2));
    }

    public Redis32Client(String host, int port, Duration timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
    }

    public String ping() {
        Object reply = call("PING");
        return reply == null ? null : reply.toString();
    }

    public Optional<String> get(String key) {
        Object reply = call("GET", key);
        return reply == null ? Optional.empty() : Optional.of(reply.toString());
    }

    public void setEx(String key, String value, int seconds) {
        call("SET", key, value, "EX", String.valueOf(seconds));
    }

    public void del(String key) {
        call("DEL", key);
    }

    public long publish(String channel, String message) {
        Object reply = call("PUBLISH", channel, message);
        return reply instanceof Long number ? number : Long.parseLong(String.valueOf(reply));
    }

    /**
     * Blocking SUBSCRIBE until one message or timeout. Product invalidation is
     * fire-and-forget PUBLISH plus DB version polling; this is the test / loop
     * primitive, not a Sentinel failover client.
     */
    public Optional<String> subscribeOnce(String channel, Duration wait) {
        try (Socket socket = open()) {
            write(socket, encode("SUBSCRIBE", channel));
            readReply(socket.getInputStream()); // subscribe ack
            socket.setSoTimeout((int) Math.max(1, wait.toMillis()));
            Object message = readReply(socket.getInputStream());
            if (message instanceof List<?> list && list.size() >= 3) {
                Object payload = list.get(2);
                return payload == null ? Optional.empty() : Optional.of(payload.toString());
            }
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /**
     * RESP2 SUBSCRIBE loop used by the invalidate-only listener. Timeouts are
     * empty polls; failures retry while {@code running} stays true.
     */
    public void subscribeLoop(String channel, Consumer<String> onMessage, java.util.concurrent.atomic.AtomicBoolean running) {
        while (running != null && running.get()) {
            try {
                Optional<String> payload = subscribeOnce(channel, Duration.ofSeconds(2));
                if (payload.isPresent() && running.get() && onMessage != null) {
                    onMessage.accept(payload.get());
                }
            } catch (RuntimeException ex) {
                if (running.get()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    public void publishAndIgnoreFailure(String channel, String message, Consumer<Exception> onFailure) {
        try {
            publish(channel, message);
        } catch (RuntimeException ex) {
            if (onFailure != null) {
                onFailure.accept(ex);
            }
        }
    }

    @Override
    public void close() {
        // connections are per-call
    }

    private Object call(String... parts) {
        try (Socket socket = open()) {
            write(socket, encode(parts));
            return readReply(socket.getInputStream());
        } catch (IOException ex) {
            throw new RedisUnavailableException("redis 3.2 command failed: " + parts[0], ex);
        }
    }

    private Socket open() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
        socket.setSoTimeout((int) timeout.toMillis());
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void write(Socket socket, byte[] payload) throws IOException {
        socket.getOutputStream().write(payload);
        socket.getOutputStream().flush();
    }

    static byte[] encode(String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] raw = part.getBytes(StandardCharsets.UTF_8);
            builder.append('$').append(raw.length).append("\r\n").append(part).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Object readReply(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix < 0) {
            throw new IOException("redis eof");
        }
        return switch (prefix) {
            case '+' -> readLine(in);
            case '-' -> throw new RedisUnavailableException(readLine(in));
            case ':' -> Long.parseLong(readLine(in));
            case '$' -> {
                int len = Integer.parseInt(readLine(in));
                if (len < 0) {
                    yield null;
                }
                byte[] buf = in.readNBytes(len);
                if (in.read() != '\r' || in.read() != '\n') {
                    throw new IOException("missing crlf after bulk");
                }
                yield new String(buf, StandardCharsets.UTF_8);
            }
            case '*' -> {
                int count = Integer.parseInt(readLine(in));
                if (count < 0) {
                    yield null;
                }
                List<Object> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    items.add(readReply(in));
                }
                yield items;
            }
            default -> throw new IOException("unknown RESP prefix " + prefix);
        };
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int current;
        while ((current = in.read()) >= 0) {
            if (current == '\r') {
                int next = in.read();
                if (next != '\n' && next >= 0) {
                    buf.write(current);
                    buf.write(next);
                    continue;
                }
                break;
            }
            buf.write(current);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    public static final class RedisUnavailableException extends RuntimeException {
        public RedisUnavailableException(String message) {
            super(message);
        }

        public RedisUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
