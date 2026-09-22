package one.nexus.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wire format for failed API responses: {@code code / message / trace_id / details}.
 */
public record ApiError(String code, String message, String traceId, Map<String, Object> details) {
    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(traceId, "traceId");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("trace_id", traceId);
        body.put("details", details);
        return body;
    }

    public static ApiError of(ErrorCode errorCode, String message, String traceId, Map<String, Object> details) {
        return new ApiError(errorCode.code(), message, traceId, details);
    }
}
