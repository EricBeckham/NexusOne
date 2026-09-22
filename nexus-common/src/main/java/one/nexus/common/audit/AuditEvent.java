package one.nexus.common.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical audit capture shape for M0-11. Hash-chain metadata lives on the
 * chained store record in {@code nexus-audit-core}, not here.
 *
 * <p>{@code details} must already be redacted. Sensitive values must never be
 * placed in this record, logs, or the hash-chain payload.
 */
public record AuditEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        AuditCategory category,
        String eventType,
        UUID tenantId,
        UUID actorSubjectId,
        UUID actorTenantId,
        boolean platformContext,
        String reason,
        String traceId,
        String correlationId,
        String causationId,
        String outcome,
        String resourceType,
        String resourceId,
        Map<String, String> details) {

    public static final int VERSION = 1;
    public static final String OUTCOME_ACCEPTED = "ACCEPTED";
    public static final String OUTCOME_REJECTED = "REJECTED";

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        Objects.requireNonNull(category, "category");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (!OUTCOME_ACCEPTED.equals(outcome) && !OUTCOME_REJECTED.equals(outcome)) {
            throw new IllegalArgumentException("outcome must be ACCEPTED or REJECTED");
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType is required");
        }
        details = canonicalize(details);
    }

    public static Map<String, String> canonicalize(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        details.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        throw new IllegalArgumentException("detail key is required");
                    }
                    copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }
}
