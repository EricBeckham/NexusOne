package one.nexus.isolation.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    static void append(StringBuilder sql, List<Object> args, Object value) {
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
                args.add(items.get(i));
            }
            sql.append(')');
            return;
        }
        sql.append(" = ?");
        args.add(value);
    }
}
