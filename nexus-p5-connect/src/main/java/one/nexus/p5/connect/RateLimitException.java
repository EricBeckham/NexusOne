package one.nexus.p5.connect;

public final class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
