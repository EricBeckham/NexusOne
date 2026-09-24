package one.nexus.bootstrap.web;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/** Read-only platform audit search. Sensitive {@code details} are intentionally never selected. */
@Service
public class PlatformAuditSearchService {
    private static final int MAX_PAGE_SIZE = 200;

    public record Filter(Instant from, Instant to, UUID tenantId, UUID actorId, String action,
                         String resource, String result, String traceId, int page, int size) {}

    public record Event(UUID eventId, Instant occurredAt, UUID tenantId, UUID actorId, String action,
                        String resourceType, String resourceId, String result, String traceId,
                        boolean platformContext) {}

    public record Result(List<Event> items, long total, int page, int size) {
        public Result { items = List.copyOf(items); }
    }

    private static final RowMapper<Event> EVENT_MAPPER = PlatformAuditSearchService::map;
    private final JdbcTemplate jdbc;

    public PlatformAuditSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result search(Filter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new IllegalArgumentException("from must be earlier than or equal to to");
        }
        if (filter.page() < 1 || filter.size() < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }
        int size = Math.min(filter.size(), MAX_PAGE_SIZE);
        long offset = ((long) filter.page() - 1) * size;
        Query query = where(filter);
        String select = "SELECT event_id, occurred_at, tenant_id, actor_subject_id, event_type, "
                + "resource_type, resource_id, outcome, trace_id, platform_context FROM platform.audit_event";
        List<Object> pageArgs = new ArrayList<>(query.args());
        pageArgs.add(size);
        pageArgs.add(offset);
        List<Event> rows = jdbc.query(
                select + query.sql() + " ORDER BY occurred_at DESC, event_id DESC LIMIT ? OFFSET ?",
                EVENT_MAPPER,
                pageArgs.toArray());
        Long total = jdbc.queryForObject("SELECT count(*) FROM platform.audit_event" + query.sql(),
                Long.class, query.args().toArray());
        return new Result(rows, total == null ? 0 : total, filter.page(), size);
    }

    private static Query where(Filter filter) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (filter.from() != null) add(clauses, args, "occurred_at >= ?", Timestamp.from(filter.from()));
        if (filter.to() != null) add(clauses, args, "occurred_at <= ?", Timestamp.from(filter.to()));
        if (filter.tenantId() != null) add(clauses, args, "tenant_id = ?", filter.tenantId());
        if (filter.actorId() != null) add(clauses, args, "actor_subject_id = ?", filter.actorId());
        if (hasText(filter.action())) add(clauses, args, "event_type = ?", filter.action().trim());
        if (hasText(filter.resource())) {
            clauses.add("(resource_type = ? OR resource_id = ?)");
            args.add(filter.resource().trim());
            args.add(filter.resource().trim());
        }
        if (hasText(filter.result())) add(clauses, args, "outcome = ?", filter.result().trim().toUpperCase(Locale.ROOT));
        if (hasText(filter.traceId())) add(clauses, args, "trace_id = ?", filter.traceId().trim());
        return new Query(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), List.copyOf(args));
    }

    private static void add(List<String> clauses, List<Object> args, String clause, Object value) {
        clauses.add(clause);
        args.add(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Event map(ResultSet row, int index) throws SQLException {
        return new Event(
                row.getObject("event_id", UUID.class), row.getTimestamp("occurred_at").toInstant(),
                row.getObject("tenant_id", UUID.class), row.getObject("actor_subject_id", UUID.class),
                row.getString("event_type"), row.getString("resource_type"), row.getString("resource_id"),
                row.getString("outcome"), row.getString("trace_id"), row.getBoolean("platform_context"));
    }

    private record Query(String sql, List<Object> args) {}
}
