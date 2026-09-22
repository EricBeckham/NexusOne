package one.nexus.audit.recovery;

import java.util.List;
import java.util.Objects;

public record RecoveryDryRunResult(
        RecoveryKind kind,
        String status,
        String toolVersion,
        List<String> notes,
        boolean executed,
        boolean profileAccepted) {

    public RecoveryDryRunResult {
        Objects.requireNonNull(kind, "kind");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        notes = List.copyOf(notes == null ? List.of() : notes);
        if (executed && profileAccepted) {
            throw new IllegalArgumentException("this port must not claim Profile acceptance");
        }
    }
}
