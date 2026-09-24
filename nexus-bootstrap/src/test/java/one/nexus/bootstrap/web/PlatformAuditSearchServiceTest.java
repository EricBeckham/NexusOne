package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class PlatformAuditSearchServiceTest {
    @Test
    void appliesFiltersWithBoundParametersAndNeverSelectsSensitiveDetails() {
        UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
        UUID actor = UUID.fromString("01900000-0000-7000-8000-000000000102");
        var event = new PlatformAuditSearchService.Event(UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"),
                tenant, actor, "p1.tenant.disabled", "tenant", "acme", "REJECTED", "trace-42", true);
        var jdbc = new RecordingJdbcTemplate(List.of(event), 1L);
        var service = new PlatformAuditSearchService(jdbc);
        var filter = new PlatformAuditSearchService.Filter(
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"),
                tenant, actor, "p1.tenant.disabled", "acme", "rejected", "trace-42", 2, 25);

        var result = service.search(filter);

        assertEquals(1, result.total());
        assertEquals(2, result.page());
        assertEquals(25, result.size());
        assertEquals(List.of(event), result.items());
        assertTrue(jdbc.selectSql.contains("tenant_id = ?"));
        assertTrue(jdbc.selectSql.contains("actor_subject_id = ?"));
        assertTrue(jdbc.selectSql.contains("event_type = ?"));
        assertTrue(jdbc.selectSql.contains("resource_type = ? OR resource_id = ?"));
        assertTrue(jdbc.selectSql.contains("outcome = ?"));
        assertTrue(jdbc.selectSql.contains("trace_id = ?"));
        assertFalse(jdbc.selectSql.toLowerCase(java.util.Locale.ROOT).contains("details"));
        assertEquals(tenant, jdbc.selectArgs[2]);
        assertEquals(actor, jdbc.selectArgs[3]);
        assertEquals("REJECTED", jdbc.selectArgs[7]);
    }

    @Test
    void boundsPageSizeAndRejectsInvalidRanges() {
        var jdbc = new RecordingJdbcTemplate(List.of(), 0L);
        var service = new PlatformAuditSearchService(jdbc);

        var result = service.search(new PlatformAuditSearchService.Filter(null, null, null, null,
                null, null, null, null, 1, 1000));

        assertEquals(200, result.size());
        assertThrows(IllegalArgumentException.class, () -> service.search(
                new PlatformAuditSearchService.Filter(Instant.EPOCH, Instant.EPOCH.minusSeconds(1), null, null,
                        null, null, null, null, 1, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.search(
                new PlatformAuditSearchService.Filter(null, null, null, null, null, null, null, null, 0, 10)));
        assertEquals(1, jdbc.queryCount);
    }

    private static final class RecordingJdbcTemplate extends org.springframework.jdbc.core.JdbcTemplate {
        private final List<PlatformAuditSearchService.Event> rows;
        private final Long total;
        private String selectSql;
        private Object[] selectArgs;
        private int queryCount;

        private RecordingJdbcTemplate(List<PlatformAuditSearchService.Event> rows, Long total) {
            this.rows = rows;
            this.total = total;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            queryCount++;
            selectSql = sql;
            selectArgs = args;
            return (List<T>) rows;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (type == Long.class) return type.cast(total);
            throw new UnsupportedOperationException("unexpected query type");
        }
    }
}
