package one.nexus.common.trace;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import one.nexus.common.id.UuidV7;
import org.slf4j.MDC;

/**
 * Trace ID for logs, API errors, and async hand-off. Prefers an inbound
 * {@code X-Trace-Id} or W3C {@code traceparent}; otherwise issues a UUIDv7.
 */
public final class TraceIds {
    public static final String HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_KEY = "traceId";
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern OTEL_TRACE = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern TRACEPARENT = Pattern.compile(
            "00-([0-9a-fA-F]{32})-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}");

    private TraceIds() {}

    public static String newId() {
        return UuidV7.generate().toString();
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return UUID_TEXT.matcher(value).matches() || OTEL_TRACE.matcher(value).matches();
    }

    public static String resolve(String incomingHeader, String traceparent) {
        if (isValid(incomingHeader)) {
            return incomingHeader.trim();
        }
        Optional<String> fromParent = fromTraceparent(traceparent);
        return fromParent.orElseGet(TraceIds::newId);
    }

    public static Optional<String> fromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Optional.empty();
        }
        var matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).toLowerCase(Locale.ROOT));
    }

    public static void putMdc(String traceId) {
        MDC.put(MDC_KEY, traceId);
        MDC.put("trace_id", traceId);
    }

    public static void clearMdc() {
        MDC.remove(MDC_KEY);
        MDC.remove("trace_id");
    }

    public static String currentOrUnknown() {
        String id = MDC.get(MDC_KEY);
        return (id == null || id.isBlank()) ? "missing" : id;
    }

    public static Optional<String> current() {
        String id = MDC.get(MDC_KEY);
        if (id == null || id.isBlank() || "missing".equals(id)) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    /**
     * Fields safe for ECS/JSON logs. Matches M0-01 {@code TraceIdFilter} keys
     * ({@code traceId} / {@code trace_id}) plus an optional correlation id.
     * Never include secrets, tokens, or audit detail maps.
     */
    public static java.util.Map<String, String> logFields() {
        return logFields(currentOrUnknown(), null);
    }

    public static java.util.Map<String, String> logFields(String traceId, String correlationId) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        String id = (traceId == null || traceId.isBlank()) ? "missing" : traceId;
        fields.put(MDC_KEY, id);
        fields.put("trace_id", id);
        if (correlationId != null && !correlationId.isBlank()) {
            fields.put("correlation_id", correlationId);
        }
        return java.util.Map.copyOf(fields);
    }

    /** HTTP requests use the trace id as the correlation id unless a caller supplies one. */
    public static String correlationOrTrace(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return currentOrUnknown();
    }
}
