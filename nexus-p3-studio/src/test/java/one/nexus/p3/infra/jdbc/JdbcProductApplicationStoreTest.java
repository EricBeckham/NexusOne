package one.nexus.p3.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import one.nexus.p3.port.ProductApplicationStore.NewApplication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** H10: PostgreSQL JDBC cannot infer a SQL type for {@link Instant}; timestamps must bind as {@link Timestamp}. */
class JdbcProductApplicationStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-25T08:00:00Z");

    @Test
    void insertRunningBindsTimestampsNotInstants() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new JdbcProductApplicationStore(jdbc).insertRunning(new NewApplication(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{}", UUID.randomUUID(), NOW));

        Object[] args = capturedArgs(jdbc);
        assertNoInstant(args);
        assertEquals(Timestamp.from(NOW), args[7]);
        assertEquals(Timestamp.from(NOW), args[8]);
    }

    @Test
    void attachProcessInstanceBindsTimestampNotInstant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new JdbcProductApplicationStore(jdbc).attachProcessInstance(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);

        Object[] args = capturedArgs(jdbc);
        assertNoInstant(args);
        assertEquals(Timestamp.from(NOW), args[1]);
    }

    private static Object[] capturedArgs(JdbcTemplate jdbc) {
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        return args.getValue();
    }

    private static void assertNoInstant(Object[] args) {
        assertFalse(Arrays.stream(args).anyMatch(Instant.class::isInstance), () -> Arrays.toString(args));
    }
}
