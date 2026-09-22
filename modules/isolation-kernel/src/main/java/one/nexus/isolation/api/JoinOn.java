package one.nexus.isolation.api;

import java.util.Objects;

/** Equality join predicate. Identifiers must be registry aliases/columns; no SQL text. */
public record JoinOn(String leftAlias, String leftColumn, String rightAlias, String rightColumn) {
    public JoinOn {
        Objects.requireNonNull(leftAlias, "leftAlias");
        Objects.requireNonNull(leftColumn, "leftColumn");
        Objects.requireNonNull(rightAlias, "rightAlias");
        Objects.requireNonNull(rightColumn, "rightColumn");
    }
}
