package one.nexus.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Database timestamps are UTC {@code timestamptz}. Tenant timezones are
 * display/parse only.
 */
public final class Utc {
    public static final Clock CLOCK = Clock.systemUTC();

    private Utc() {}

    public static Instant now() {
        return Instant.now(CLOCK);
    }

    public static OffsetDateTime nowOffset() {
        return OffsetDateTime.now(CLOCK).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
