package one.nexus.common.error;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Error codes follow {@code 产品.模块.编号} from the V1.1 tech framework.
 *
 * <p>Examples: {@code CORE.API.0001}, {@code P1.TENANT.0001}, {@code CORE.CTX.0001}.
 * Stable codes are part of the public API; do not reuse a number for a new meaning.
 */
public record ErrorCode(String product, String module, int number) {
    private static final Pattern TOKEN = Pattern.compile("[A-Z][A-Z0-9]{0,15}");
    public static final ErrorCode API_BAD_REQUEST = of("CORE", "API", 1);
    public static final ErrorCode API_NOT_FOUND = of("CORE", "API", 2);
    public static final ErrorCode API_CONFLICT = of("CORE", "API", 3);
    public static final ErrorCode API_INTERNAL = of("CORE", "API", 99);
    public static final ErrorCode CTX_MISSING_TENANT = of("CORE", "CTX", 1);
    public static final ErrorCode CTX_INVALID_PLATFORM = of("CORE", "CTX", 2);
    public static final ErrorCode CTX_MISSING_ACTOR = of("CORE", "CTX", 3);
    public static final ErrorCode JOB_UNKNOWN_TYPE = of("CORE", "JOB", 1);
    public static final ErrorCode JOB_DEAD = of("CORE", "JOB", 2);
    public static final ErrorCode EVT_UNKNOWN_DESTINATION = of("CORE", "EVT", 1);
    public static final ErrorCode EVT_DISPATCH_FAILED = of("CORE", "EVT", 2);
    public static final ErrorCode CRYPTO_SUITE_MISMATCH = of("CORE", "CRYPTO", 1);
    public static final ErrorCode CRYPTO_KEY_UNKNOWN = of("CORE", "CRYPTO", 2);
    public static final ErrorCode CRYPTO_INTEGRITY = of("CORE", "CRYPTO", 3);
    public static final ErrorCode SECRET_NOT_FOUND = of("CORE", "SECRET", 1);
    public static final ErrorCode SECRET_PLAINTEXT_FORBIDDEN = of("CORE", "SECRET", 2);
    public static final ErrorCode AUDIT_CHAIN_BROKEN = of("CORE", "AUDIT", 1);
    public static final ErrorCode AUDIT_APPEND_ONLY = of("CORE", "AUDIT", 2);
    public static final ErrorCode RECOVERY_DRY_RUN_ONLY = of("CORE", "RECOVERY", 1);

    public ErrorCode {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(module, "module");
        if (!TOKEN.matcher(product).matches()) {
            throw new IllegalArgumentException("invalid product token: " + product);
        }
        if (!TOKEN.matcher(module).matches()) {
            throw new IllegalArgumentException("invalid module token: " + module);
        }
        if (number < 1 || number > 9999) {
            throw new IllegalArgumentException("number must be 1..9999");
        }
    }

    public static ErrorCode of(String product, String module, int number) {
        return new ErrorCode(product, module, number);
    }

    public static ErrorCode parse(String code) {
        Objects.requireNonNull(code, "code");
        String[] parts = code.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected PRODUCT.MODULE.NUMBER: " + code);
        }
        return of(parts[0], parts[1], Integer.parseInt(parts[2]));
    }

    public String code() {
        return product + "." + module + "." + String.format("%04d", number);
    }

    @Override
    public String toString() {
        return code();
    }
}
