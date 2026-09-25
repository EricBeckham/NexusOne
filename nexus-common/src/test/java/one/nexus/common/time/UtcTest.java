package one.nexus.common.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UtcTest {
    @Test
    void sqlTimestampPreservesTheInstantAndPassesNullThrough() {
        Instant instant = Instant.parse("2026-09-25T08:00:00.123456Z");
        Timestamp timestamp = Utc.sqlTimestamp(instant);
        assertEquals(instant, timestamp.toInstant());
        assertNull(Utc.sqlTimestamp(null));
    }
}
