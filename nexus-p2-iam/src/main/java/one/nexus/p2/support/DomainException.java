package one.nexus.p2.support;

public final class DomainException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public DomainException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static DomainException notFound(String message) {
        return new DomainException("P2.IAM.NOT_FOUND", 404, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException("P2.IAM.CONFLICT", 409, message);
    }

    public static DomainException unauthorized(String message) {
        return new DomainException("P2.IAM.UNAUTHORIZED", 401, message);
    }

    public static DomainException forbidden(String message) {
        return new DomainException("P2.IAM.FORBIDDEN", 403, message);
    }

    public static DomainException mfaRequired(String message) {
        return new DomainException("P2.IAM.MFA_REQUIRED", 403, message);
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(code, 400, message);
    }
}
