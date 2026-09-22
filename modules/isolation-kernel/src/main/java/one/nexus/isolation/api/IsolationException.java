package one.nexus.isolation.api;

import java.util.Objects;

/** Guard reject. Always thrown before JDBC / cache / file / async side effects. */
public final class IsolationException extends RuntimeException {
    private final IsolationErrorCode error;
    private final String traceId;

    public IsolationException(IsolationErrorCode error, String traceId) {
        this(error, traceId, error.defaultMessage());
    }

    public IsolationException(IsolationErrorCode error, String traceId, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.error = Objects.requireNonNull(error, "error");
        this.traceId = traceId == null || traceId.isBlank() ? "missing-trace" : traceId;
    }

    public IsolationErrorCode error() {
        return error;
    }

    public String code() {
        return error.code();
    }

    public String traceId() {
        return traceId;
    }
}
