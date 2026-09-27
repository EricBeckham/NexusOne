package one.nexus.p3.completion;

import java.time.Instant;
import java.util.UUID;

/** FA4: one execution of a {@link CompletionAction} for one finished form. At most one per action and source record. */
public record CompletionActionRun(
        UUID id,
        UUID tenantId,
        UUID actionId,
        String sourceLedgerCode,
        UUID sourceRecordId,
        UUID processInstanceId,
        String outcome,
        Status status,
        UUID targetRecordId,
        int attempts,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { PENDING, DONE, FAILED }
}
