package one.nexus.p1.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable tenant business code. Same character class as P0-08 ledger identifiers so
 * {@code l_{tenant_code}_{ledger_code}} stays within PostgreSQL identifier length.
 */
public final class TenantCode {

    public static final String PLATFORM = "platform";
    public static final int MAX_LENGTH = 24;
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private TenantCode() {
    }

    public static String requireValid(String code) {
        if (code == null || !PATTERN.matcher(code).matches()) {
            throw new DomainException(
                    ErrorCode.TENANT_INVALID_CODE,
                    "tenant code must match ^[a-z][a-z0-9_]{0,23}$");
        }
        return code;
    }

    public static String requireBusiness(String code) {
        String valid = requireValid(code);
        if (PLATFORM.equals(valid)) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "code 'platform' is reserved");
        }
        return valid;
    }

    public static String slugFromName(String name) {
        String raw = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        raw = raw.replaceAll("^_+", "").replaceAll("_+$", "");
        if (raw.isBlank()) {
            raw = "tenant";
        }
        if (raw.charAt(0) >= '0' && raw.charAt(0) <= '9') {
            raw = "t" + raw;
        }
        if (raw.length() > 19) {
            raw = raw.substring(0, 19);
        }
        return raw;
    }

    public static boolean isWellFormed(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }

    public static boolean reserved(String code) {
        return PLATFORM.equals(code);
    }

    public static void requireNotBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "tenant name is required");
        }
        Objects.requireNonNull(name);
    }
}
