package one.nexus.p2.support;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Time-ordered UUIDv7. Java 21 has no standard UUID v7 factory. */
public final class Ids {
    private Ids() {}

    public static UUID v7() {
        return v7(Clock.systemUTC());
    }

    public static UUID v7(Clock clock) {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        long ms = clock.millis();
        bytes[0] = (byte) (ms >>> 40);
        bytes[1] = (byte) (ms >>> 32);
        bytes[2] = (byte) (ms >>> 24);
        bytes[3] = (byte) (ms >>> 16);
        bytes[4] = (byte) (ms >>> 8);
        bytes[5] = (byte) ms;
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
