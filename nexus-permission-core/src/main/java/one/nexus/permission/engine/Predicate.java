package one.nexus.permission.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Abstract row predicate. P0-01 / isolation kernel compile this tree to a
 * parameterized Query AST; this module never emits raw SQL identifiers from input.
 */
public record Predicate(String op, List<Object> args) {
    public static final Predicate TRUE = new Predicate("true", List.of());
    public static final Predicate FALSE = new Predicate("false", List.of());

    public Predicate {
        Objects.requireNonNull(op, "op");
        args = List.copyOf(args == null ? List.of() : args);
    }

    public static Predicate atom(String column, String operator, Object value) {
        return new Predicate("atom", List.of(column, operator, value));
    }

    public static Predicate anyOf(Predicate... items) {
        return anyOf(Arrays.asList(items));
    }

    public static Predicate anyOf(List<Predicate> items) {
        List<Predicate> kept = new ArrayList<>();
        for (Predicate item : items) {
            if (item == null || "false".equals(item.op())) {
                continue;
            }
            if ("true".equals(item.op())) {
                return TRUE;
            }
            kept.add(item);
        }
        if (kept.isEmpty()) {
            return FALSE;
        }
        return kept.size() == 1 ? kept.getFirst() : new Predicate("or", List.copyOf(kept));
    }

    public static Predicate allOf(Predicate... items) {
        return allOf(Arrays.asList(items));
    }

    public static Predicate allOf(List<Predicate> items) {
        List<Predicate> kept = new ArrayList<>();
        for (Predicate item : items) {
            if (item == null || "true".equals(item.op())) {
                continue;
            }
            if ("false".equals(item.op())) {
                return FALSE;
            }
            kept.add(item);
        }
        if (kept.isEmpty()) {
            return TRUE;
        }
        return kept.size() == 1 ? kept.getFirst() : new Predicate("and", List.copyOf(kept));
    }
}
