package one.nexus.audit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoSuite;

/**
 * Deterministic JSON for one chained audit event stored as a cold object.
 * Field order is fixed. Details reuse {@link CanonicalAudit} JSON.
 */
public final class AuditColdObjectCodec {
    private AuditColdObjectCodec() {}

    public static byte[] encode(ChainedAuditEvent linked) {
        AuditEvent event = linked.event();
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "chain_index", linked.chainIndex(), true);
        field(json, "event_id", event.eventId().toString(), false);
        field(json, "event_version", event.eventVersion(), false);
        field(json, "occurred_at", event.occurredAt().toString(), false);
        field(json, "category", event.category().name(), false);
        field(json, "event_type", event.eventType(), false);
        nullable(json, "tenant_id", event.tenantId());
        nullable(json, "actor_subject_id", event.actorSubjectId());
        nullable(json, "actor_tenant_id", event.actorTenantId());
        field(json, "platform_context", event.platformContext(), false);
        nullable(json, "reason", event.reason());
        field(json, "trace_id", event.traceId(), false);
        nullable(json, "correlation_id", event.correlationId());
        nullable(json, "causation_id", event.causationId());
        field(json, "outcome", event.outcome(), false);
        field(json, "resource_type", event.resourceType(), false);
        nullable(json, "resource_id", event.resourceId());
        json.append(",\"details\":").append(CanonicalAudit.detailsJson(event.details()));
        field(json, "previous_hash", HexFormat.of().formatHex(linked.previousHash()), false);
        field(json, "event_hash", HexFormat.of().formatHex(linked.eventHash()), false);
        field(json, "cipher_suite", linked.suite().name(), false);
        field(json, "digest_key_version", linked.digestKeyVersion(), false);
        json.append('}');
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static ChainedAuditEvent decode(byte[] body) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("cold object body is empty");
        }
        Map<String, String> fields = parseFlat(new String(body, StandardCharsets.UTF_8));
        AuditEvent event = new AuditEvent(
                UUID.fromString(require(fields, "event_id")),
                Integer.parseInt(require(fields, "event_version")),
                Instant.parse(require(fields, "occurred_at")),
                AuditCategory.valueOf(require(fields, "category")),
                require(fields, "event_type"),
                uuid(fields.get("tenant_id")),
                uuid(fields.get("actor_subject_id")),
                uuid(fields.get("actor_tenant_id")),
                Boolean.parseBoolean(require(fields, "platform_context")),
                emptyToNull(fields.get("reason")),
                require(fields, "trace_id"),
                emptyToNull(fields.get("correlation_id")),
                emptyToNull(fields.get("causation_id")),
                require(fields, "outcome"),
                require(fields, "resource_type"),
                emptyToNull(fields.get("resource_id")),
                CanonicalAudit.parseDetailsJson(fields.getOrDefault("details", "{}")));
        return new ChainedAuditEvent(
                Long.parseLong(require(fields, "chain_index")),
                event,
                HexFormat.of().parseHex(fields.getOrDefault("previous_hash", "")),
                HexFormat.of().parseHex(require(fields, "event_hash")),
                CryptoSuite.valueOf(require(fields, "cipher_suite")),
                require(fields, "digest_key_version"));
    }

    private static void field(StringBuilder json, String name, long value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":").append(value);
    }

    private static void field(StringBuilder json, String name, boolean value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":").append(value);
    }

    private static void field(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static void nullable(StringBuilder json, String name, Object value) {
        json.append(",\"").append(name).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cold object missing " + key);
        }
        return value;
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    /**
     * Parses a single JSON object whose values are string / number / boolean / null
     * plus a nested {@code details} object.
     */
    static Map<String, String> parseFlat(String json) {
        String text = json.trim();
        if (text.isEmpty() || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            throw new IllegalArgumentException("cold object must be a JSON object");
        }
        Map<String, String> out = new LinkedHashMap<>();
        int i = 1;
        int end = text.length() - 1;
        while (i < end) {
            while (i < end && (text.charAt(i) == ',' || Character.isWhitespace(text.charAt(i)))) {
                i++;
            }
            if (i >= end) {
                break;
            }
            if (text.charAt(i) != '"') {
                throw new IllegalArgumentException("expected field name");
            }
            Parse key = parseString(text, i);
            i = key.next;
            while (i < end && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= end || text.charAt(i) != ':') {
                throw new IllegalArgumentException("expected colon");
            }
            i++;
            while (i < end && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= end) {
                throw new IllegalArgumentException("truncated value");
            }
            char ch = text.charAt(i);
            if (ch == '"') {
                Parse value = parseString(text, i);
                out.put(key.value, value.value);
                i = value.next;
            } else if (ch == '{') {
                int close = matchingBrace(text, i);
                out.put(key.value, text.substring(i, close + 1));
                i = close + 1;
            } else if (text.startsWith("null", i)) {
                out.put(key.value, "");
                i += 4;
            } else if (text.startsWith("true", i)) {
                out.put(key.value, "true");
                i += 4;
            } else if (text.startsWith("false", i)) {
                out.put(key.value, "false");
                i += 5;
            } else {
                int start = i;
                while (i < end && "+-0123456789.eE".indexOf(text.charAt(i)) >= 0) {
                    i++;
                }
                out.put(key.value, text.substring(start, i));
            }
        }
        return out;
    }

    private record Parse(String value, int next) {}

    private static Parse parseString(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '"') {
            throw new IllegalArgumentException("expected string");
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

    private static int matchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("unterminated object");
    }
}
