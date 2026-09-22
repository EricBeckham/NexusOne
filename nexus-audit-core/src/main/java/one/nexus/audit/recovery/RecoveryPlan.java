package one.nexus.audit.recovery;

import java.util.List;
import java.util.Objects;

/**
 * Documented restore procedure. Executing a real restore is out of band;
 * {@link RecoveryPort} only describes or dry-runs.
 */
public record RecoveryPlan(
        RecoveryKind kind,
        String title,
        List<String> steps,
        String runbookPath,
        boolean profileAccepted) {

    public RecoveryPlan {
        Objects.requireNonNull(kind, "kind");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        steps = List.copyOf(steps == null ? List.of() : steps);
        if (runbookPath == null || runbookPath.isBlank()) {
            throw new IllegalArgumentException("runbookPath is required");
        }
    }
}
