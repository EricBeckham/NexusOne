package one.nexus.p1.domain;

/**
 * Error codes follow 产品.模块.编号 (tech framework §3.1).
 */
public enum ErrorCode {
    TENANT_NOT_FOUND("P1.TENANT.001"),
    TENANT_CODE_CONFLICT("P1.TENANT.002"),
    TENANT_INVALID_STATE("P1.TENANT.003"),
    TENANT_PLATFORM_PROTECTED("P1.TENANT.004"),
    TENANT_INVALID_CODE("P1.TENANT.005"),
    TENANT_CONFIRM_REQUIRED("P1.TENANT.006"),
    QUOTA_EXCEEDED("P1.QUOTA.001"),
    QUOTA_INVALID("P1.QUOTA.002"),
    CONTEXT_PLATFORM_REQUIRED("P1.CONTEXT.001"),
    CONTEXT_TENANT_MISMATCH("P1.CONTEXT.002"),
    ADMIN_INIT_FAILED("P1.ADMIN.001"),
    DUTY_DENIED("P1.DUTY.001");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
