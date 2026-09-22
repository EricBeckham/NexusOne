package one.nexus.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed failure carrying a stable {@link ErrorCode}. HTTP mapping lives in bootstrap.
 */
public class NexusException extends RuntimeException {
    private final ErrorCode errorCode;
    private final int httpStatus;
    private final Map<String, Object> details;

    public NexusException(ErrorCode errorCode, int httpStatus, String message) {
        this(errorCode, httpStatus, message, Map.of());
    }

    public NexusException(ErrorCode errorCode, int httpStatus, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be 4xx/5xx");
        }
        this.httpStatus = httpStatus;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static NexusException badRequest(String message) {
        return new NexusException(ErrorCode.API_BAD_REQUEST, 400, message);
    }

    public static NexusException notFound(String message) {
        return new NexusException(ErrorCode.API_NOT_FOUND, 404, message);
    }

    public static NexusException missingTenant() {
        return new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required");
    }
}
