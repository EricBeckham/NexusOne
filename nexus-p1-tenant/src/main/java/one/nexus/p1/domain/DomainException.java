package one.nexus.p1.domain;

import java.util.Map;

public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public DomainException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
