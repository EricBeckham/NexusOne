package one.nexus.p3.completion;

import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * FA4: the outbox payload of {@code p3.approval.completed}. Ids, codes and person ids only — never field values.
 * {@code actionId} is set only by a manual retry, to replay one action.
 */
public record CompletionEvent(
        String sourceLedgerCode,
        UUID sourceRecordId,
        UUID processInstanceId,
        String processKey,
        String outcome,
        String initiator,
        String approver,
        String completedAt,
        UUID actionId
) {
    public static final String DESTINATION = "p3.completion";
    public static final String EVENT_TYPE = "p3.approval.completed";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("source_ledger_code", sourceLedgerCode);
        body.put("source_record_id", String.valueOf(sourceRecordId));
        body.put("process_instance_id", processInstanceId == null ? null : processInstanceId.toString());
        body.put("process_key", processKey);
        body.put("outcome", outcome);
        body.put("initiator", initiator);
        body.put("approver", approver);
        body.put("completed_at", completedAt);
        body.put("action_id", actionId == null ? null : actionId.toString());
        return MAPPER.writeValueAsString(body);
    }

    public static CompletionEvent fromJson(String json) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(json, Map.class);
        return new CompletionEvent(
                text(body, "source_ledger_code"),
                uuid(body, "source_record_id"),
                uuid(body, "process_instance_id"),
                text(body, "process_key"),
                text(body, "outcome"),
                text(body, "initiator"),
                text(body, "approver"),
                text(body, "completed_at"),
                uuid(body, "action_id"));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String value = text(body, key);
        return value == null || value.isBlank() || "null".equals(value) ? null : UUID.fromString(value);
    }
}
