package one.nexus.isolation.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mixed-column OR of column atoms. Each branch is AND of the same filter
 * shapes as {@link QueryAst#filters()} (scalar {@code =} or single-column
 * {@code IN}). {@code tenant_id} must not appear; {@link one.nexus.isolation.query.TenantInjector}
 * still owns it as a conjunct.
 *
 * <p>This is the SELF∨DEPT leftover shape ({@code owner_id = ? OR department_id = ?}),
 * not a general boolean algebra rewrite of M0-02.
 */
public final class QueryOr {
    private final List<Map<String, Object>> branches;

    private QueryOr(List<Map<String, Object>> branches) {
        if (branches == null || branches.size() < 2) {
            throw new IllegalArgumentException("OR requires at least two branches");
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> branch : branches) {
            Objects.requireNonNull(branch, "or branch");
            if (branch.isEmpty()) {
                throw new IllegalArgumentException("OR branch cannot be empty");
            }
            copy.add(copyBranch(branch));
        }
        this.branches = List.copyOf(copy);
    }

    public static QueryOr of(List<Map<String, Object>> branches) {
        return new QueryOr(branches);
    }

    @SafeVarargs
    public static QueryOr of(Map<String, Object> first, Map<String, Object> second, Map<String, Object>... rest) {
        List<Map<String, Object>> branches = new ArrayList<>();
        branches.add(first);
        branches.add(second);
        if (rest != null) {
            Collections.addAll(branches, rest);
        }
        return new QueryOr(branches);
    }

    public List<Map<String, Object>> branches() {
        return branches;
    }

    private static Map<String, Object> copyBranch(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return value;
        }
        return List.copyOf(new ArrayList<>(collection));
    }
}
