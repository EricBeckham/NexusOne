package one.nexus.p3.completion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FA3: "when a form's approval in {@code sourceLedgerCode} / {@code processKey} ends with {@code trigger}, insert one
 * row into {@code targetLedgerCode}" (docs/planning/FA1-办结后动作设计.md). Same tenant only.
 */
public record CompletionAction(
        UUID id,
        UUID tenantId,
        String name,
        String sourceLedgerCode,
        String processKey,
        Trigger trigger,
        ActionType actionType,
        String targetLedgerCode,
        List<FieldMapping> fieldMapping,
        boolean enabled,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
    public CompletionAction {
        fieldMapping = fieldMapping == null ? List.of() : List.copyOf(fieldMapping);
    }

    public enum Trigger {
        APPROVED, REJECTED, ANY;

        public boolean matches(String outcome) {
            return this == ANY || name().equals(outcome);
        }
    }

    public enum ActionType { INSERT_ROW }

    /** Where a target field's value comes from. */
    public enum SourceKind { FIELD, CONST, SYSTEM }

    /** System variables available to a mapping (FA1 §3). */
    public enum SystemValue { SOURCE_RECORD_ID, INITIATOR, LAST_APPROVER, COMPLETED_AT, OUTCOME }

    /** {@code target} ← ({@code kind}, {@code value}). */
    public record FieldMapping(String target, SourceKind kind, String value) {}
}
