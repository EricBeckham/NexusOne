package one.nexus.isolation.api;

/**
 * Isolation error codes follow {@code 产品.模块.编号}.
 * M0-01 may wrap these in the HTTP {@code code / message / trace_id / details} envelope.
 */
public enum IsolationErrorCode {
    MISSING_CONTEXT("P1.M02.001", "missing access context"),
    CROSS_TENANT_WRITE("P1.M02.002", "cross-tenant write rejected"),
    CROSS_TENANT_READ("P1.M02.003", "cross-tenant read rejected"),
    ILLEGAL_PLATFORM_ACCESS("P1.M02.004", "illegal platform access"),
    UNREGISTERED_TABLE("P1.M02.005", "unregistered table"),
    UNREGISTERED_COLUMN("P1.M02.006", "unregistered column"),
    INVALID_IDENTIFIER("P1.M02.007", "invalid SQL identifier"),
    NATIVE_TEMPLATE_NOT_WHITELISTED("P1.M02.008", "native SQL template is not whitelisted"),
    CLIENT_TENANT_UNTRUSTED("P1.M02.009", "client-supplied tenant id is not a source of truth"),
    TENANT_PREDICATE_REQUIRED("P1.M02.010", "tenant predicate cannot be omitted"),
    UNBOUNDED_WRITE("P1.M02.011", "unbounded write forbidden"),
    TENANT_ID_IMMUTABLE("P1.M02.012", "tenant_id is immutable"),
    MEMBERSHIP_DENIED("P1.M02.013", "actor is not a member of the requested tenant"),
    CROSS_TENANT_CACHE("P1.M02.014", "cross-tenant cache access rejected"),
    CROSS_TENANT_FILE("P1.M02.015", "cross-tenant file access rejected"),
    CROSS_TENANT_ASYNC("P1.M02.016", "cross-tenant async payload rejected"),
    CONTEXT_PROPAGATION_MISSING("P1.M02.017", "tenant context missing at async/cache/file/outbox boundary"),
    PLATFORM_PATH_REVOKED("P1.M02.018", "platform path is not currently allowed"),
    CROSS_TENANT_EXPORT("P1.M02.019", "cross-tenant export rejected"),
    FIELD_POLICY_REQUIRED("P1.M02.020", "export requires FieldPolicy projection");

    private final String code;
    private final String defaultMessage;

    IsolationErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
