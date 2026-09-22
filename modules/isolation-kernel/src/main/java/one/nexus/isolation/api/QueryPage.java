package one.nexus.isolation.api;

/**
 * Restricted LIMIT/OFFSET. The compiler rejects non-positive limit, negative offset,
 * or a limit above {@link #MAX_LIMIT} as {@code UNBOUNDED_PAGE} before JDBC.
 */
public record QueryPage(int limit, int offset) {
    public static final int MAX_LIMIT = 1000;

    public static QueryPage of(int limit, int offset) {
        return new QueryPage(limit, offset);
    }
}
