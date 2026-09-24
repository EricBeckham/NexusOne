package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import one.nexus.isolation.api.QueryRange;

/**
 * Filter value shapes on {@code QueryAst.filters()} / join / subquery filters.
 * A scalar is {@code = ?}. A non-empty {@link Collection} is {@code IN (?, …)}.
 * {@code tenant_id} stays a single equality owned by {@link TenantInjector}.
 */
final class FilterAtoms {
    private FilterAtoms() {}

    static boolean isIn(Object value) {
        return value instanceof Collection<?>;
    }

    static List<Object> inValues(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("not an IN filter");
        }
        List<Object> items = new ArrayList<>();
        for (Object item : collection) {
            if (item == null) {
                throw new IllegalArgumentException("IN filter cannot contain null");
            }
            if (item instanceof Collection<?>) {
                throw new IllegalArgumentException("nested IN filter is not allowed");
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    static void append(StringBuilder sql, List<Object> args, String column, Object value) {
        // Ledger tenant_id is text; P4/P5 tenant_id is uuid. Compare both as text.
        // Inserts still use bind(), which keeps a real UUID for uuid columns.
        boolean tenantText = "tenant_id".equals(column);
        if (tenantText) {
            sql.append("::text");
        }
        if (value instanceof QueryRange range) {
            sql.append(' ').append(range.comparison().sql()).append(" ?");
            args.add(tenantText ? asText(range.value()) : bind(column, range.value()));
            return;
        }
        if (value == null) {
            sql.append(" IS NULL");
            return;
        }
        if (value instanceof Collection<?>) {
            List<Object> items = inValues(value);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("empty IN filter");
            }
            sql.append(" IN (");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append('?');
                args.add(tenantText ? asText(items.get(i)) : bind(column, items.get(i)));
            }
            sql.append(')');
            return;
        }
        sql.append(" = ?");
        args.add(tenantText ? asText(value) : bind(column, value));
    }

    private static Object asText(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return value;
    }

    static Object bind(String column, Object value) {
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        if (!"tenant_id".equals(column) || !(value instanceof String tenantId)) {
            return value;
        }
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }
}
