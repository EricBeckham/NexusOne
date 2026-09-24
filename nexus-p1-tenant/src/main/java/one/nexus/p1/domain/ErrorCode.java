package one.nexus.p1.domain;

/**
 * Error codes follow 产品.模块.编号 with a four-digit number (M0-01 / tech framework §3.1).
 */
public enum ErrorCode {
    TENANT_NOT_FOUND("P1.TENANT.0001"),
    TENANT_CODE_CONFLICT("P1.TENANT.0002"),
    TENANT_INVALID_STATE("P1.TENANT.0003"),
    TENANT_PLATFORM_PROTECTED("P1.TENANT.0004"),
    TENANT_INVALID_CODE("P1.TENANT.0005"),
    TENANT_CONFIRM_REQUIRED("P1.TENANT.0006"),
    TENANT_PARENT_CYCLE("P1.TENANT.0007"),
    QUOTA_EXCEEDED("P1.QUOTA.0001"),
    QUOTA_INVALID("P1.QUOTA.0002"),
    CONTEXT_PLATFORM_REQUIRED("P1.CONTEXT.0001"),
    CONTEXT_TENANT_MISMATCH("P1.CONTEXT.0002"),
    ADMIN_INIT_FAILED("P1.ADMIN.0001"),
    DUTY_DENIED("P1.DUTY.0001");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
