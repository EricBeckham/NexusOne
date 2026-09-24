package one.nexus.isolation.api;

import java.util.Objects;

/** Parameterized range predicate for a single registered column. */
public record QueryRange(Comparison comparison, Object value) {
    public enum Comparison {
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL("<="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">=");

        private final String sql;

        Comparison(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    public QueryRange {
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(value, "value");
        if (value instanceof java.util.Collection<?>) {
            throw new IllegalArgumentException("range value cannot be a collection");
        }
    }

    public static QueryRange lt(Object value) {
        return new QueryRange(Comparison.LESS_THAN, value);
    }

    public static QueryRange lte(Object value) {
        return new QueryRange(Comparison.LESS_THAN_OR_EQUAL, value);
    }

    public static QueryRange gt(Object value) {
        return new QueryRange(Comparison.GREATER_THAN, value);
    }

    public static QueryRange gte(Object value) {
        return new QueryRange(Comparison.GREATER_THAN_OR_EQUAL, value);
    }
}
