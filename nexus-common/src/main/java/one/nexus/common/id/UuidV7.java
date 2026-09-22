package one.nexus.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 UUIDv7 (time-ordered). Java 21 has no JDK generator; do not use
 * database sequences as public IDs.
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Clock UTC = Clock.systemUTC();

    private UuidV7() {}

    public static UUID generate() {
        return generate(UTC.millis(), RANDOM);
    }

    static UUID generate(long unixMillis, SecureRandom random) {
        if (unixMillis < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        long randA = random.nextLong() & 0x0FFFL;
        long randB = random.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long msb = (unixMillis << 16) | (0x7L << 12) | randA;
        long lsb = (0x2L << 62) | randB;
        return new UUID(msb, lsb);
    }

    public static boolean isUuidV7(UUID id) {
        return id != null && id.version() == 7 && id.variant() == 2;
    }
}
