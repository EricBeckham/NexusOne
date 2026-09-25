package one.nexus.common.time;

import java.sql.Timestamp;
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

    /**
     * JDBC bind value for a {@code timestamptz} parameter. The PostgreSQL driver cannot infer a SQL
     * type for {@link Instant}, so never bind one directly as a statement parameter.
     */
    public static Timestamp sqlTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
