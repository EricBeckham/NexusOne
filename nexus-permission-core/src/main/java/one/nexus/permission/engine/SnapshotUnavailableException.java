package one.nexus.permission.engine;

/** Fail-closed when the authoritative policy version or snapshot cannot be trusted. */
public final class SnapshotUnavailableException extends RuntimeException {
    public SnapshotUnavailableException(String message) {
        super(message);
    }

    public SnapshotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
