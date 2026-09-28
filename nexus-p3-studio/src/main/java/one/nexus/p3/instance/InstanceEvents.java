package one.nexus.p3.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parses a C4 payload. Event kind is derived from the fields because the handler does not receive event_type. */
public final class InstanceEvents {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InstanceEvents() {}

    public static InstanceEvent parse(String json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (RuntimeException ex) {
            throw invalid("instance event payload is not an object");
        }
        if (node == null || !node.isObject()) {
            throw invalid("instance event payload is not an object");
        }
        require(node, "event_id", "instance_id", "instance_revision", "product_code", "app_code", "version",
                "previous_version", "mode", "entries", "occurred_at");
        long revision = node.get("instance_revision").isNumber() ? node.get("instance_revision").asLong() : -1;
        if (revision < 1) {
            throw invalid("instance_revision must start at 1");
        }
        return new InstanceEvent(
                uuid(node, "event_id"),
                uuid(node, "instance_id"),
                revision,
                text(node, "product_code"),
                text(node, "app_code"),
                text(node, "version"),
                nullableText(node, "previous_version"),
                nullableText(node, "mode"),
                entries(node.get("entries")));
    }

    private static void require(JsonNode node, String... names) {
        for (String name : names) {
            if (!node.has(name)) {
                throw invalid("instance event is missing a required field");
            }
        }
    }

    private static UUID uuid(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) {
            throw invalid("instance event identifier is invalid");
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException ex) {
            throw invalid("instance event identifier is invalid");
        }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("instance event field is blank");
        }
        return value.asText();
    }

    private static String nullableText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid("instance event field is blank");
        }
        return value.asText();
    }

    private static List<String> entries(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw invalid("entries must be a list");
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid("entries must be a list of keys");
            }
            keys.add(item.asText());
        }
        return List.copyOf(keys);
    }

    private static P3Exception invalid(String message) {
        return new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400, message, java.util.Map.of());
    }
}
