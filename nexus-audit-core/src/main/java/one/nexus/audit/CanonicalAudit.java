package one.nexus.audit;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import one.nexus.common.audit.AuditEvent;

/**
 * Deterministic bytes hashed into the append-only chain. Field order is fixed;
 * details are already redacted and key-sorted.
 */
public final class CanonicalAudit {
    private CanonicalAudit() {}

    public static byte[] encode(long chainIndex, byte[] previousHash, AuditEvent event) {
        StringBuilder out = new StringBuilder();
        out.append("v1\n");
        out.append(chainIndex).append('\n');
        out.append(HexFormat.of().formatHex(previousHash)).append('\n');
        out.append(event.eventId()).append('\n');
        out.append(event.eventVersion()).append('\n');
        out.append(event.occurredAt()).append('\n');
        out.append(event.category().name()).append('\n');
        out.append(event.eventType()).append('\n');
        out.append(nullable(event.tenantId())).append('\n');
        out.append(nullable(event.actorSubjectId())).append('\n');
        out.append(nullable(event.actorTenantId())).append('\n');
        out.append(event.platformContext()).append('\n');
        out.append(nullable(event.reason())).append('\n');
        out.append(event.traceId()).append('\n');
        out.append(nullable(event.correlationId())).append('\n');
        out.append(nullable(event.causationId())).append('\n');
        out.append(event.outcome()).append('\n');
        out.append(event.resourceType()).append('\n');
        out.append(nullable(event.resourceId())).append('\n');
        out.append(detailsJson(event.details())).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String detailsJson(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"");
            json.append(escape(entry.getValue() == null ? "" : entry.getValue())).append('"');
        }
        json.append('}');
        return json.toString();
    }

    public static Map<String, String> parseDetailsJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        String text = json.trim();
        if (text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            throw new IllegalArgumentException("details JSON must be an object");
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        int i = 1;
        int end = text.length() - 1;
        while (i < end) {
            while (i < end && (text.charAt(i) == ',' || Character.isWhitespace(text.charAt(i)))) {
                i++;
            }
            if (i >= end) {
                break;
            }
            Parse cursor = parseString(text, i);
            String key = cursor.value;
            i = cursor.next;
            while (i < end && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= end || text.charAt(i) != ':') {
                throw new IllegalArgumentException("expected colon in details JSON");
            }
            i++;
            while (i < end && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            cursor = parseString(text, i);
            out.put(key, cursor.value);
            i = cursor.next;
        }
        return Map.copyOf(out);
    }

    private record Parse(String value, int next) {}

    private static Parse parseString(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '"') {
            throw new IllegalArgumentException("expected string in details JSON");
        }
        StringBuilder value = new StringBuilder();
        int i = start + 1;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '"') {
                return new Parse(value.toString(), i + 1);
            }
            if (ch == '\\') {
                i++;
                if (i >= text.length()) {
                    throw new IllegalArgumentException("truncated escape");
                }
                char esc = text.charAt(i);
                value.append(switch (esc) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> esc;
                });
            } else {
                value.append(ch);
            }
            i++;
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static String nullable(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}
