package one.nexus.isolation.api;

import java.util.Objects;

/** Restricted ORDER BY. Column must be a registered identifier; direction is an enum. */
public record QuerySort(String column, QuerySort.Direction direction) {
    public enum Direction {
        ASC,
        DESC
    }

    public QuerySort {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(direction, "direction");
    }

    public static QuerySort asc(String column) {
        return new QuerySort(column, Direction.ASC);
    }

    public static QuerySort desc(String column) {
        return new QuerySort(column, Direction.DESC);
    }
}
