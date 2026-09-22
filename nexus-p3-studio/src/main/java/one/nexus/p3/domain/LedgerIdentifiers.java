package one.nexus.p3.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P0-08 / P1 tenant-code character class so {@code l_{tenant_code}_{ledger_code}}
 * stays a legal PostgreSQL identifier. Isolation-kernel {@code Identifiers} is
 * the same class without the leading-letter / length cap.
 */
public final class LedgerIdentifiers {
    public static final int MAX_CODE = 24;
    public static final int MAX_TABLE = 52;
    public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");
    public static final Pattern TABLE = Pattern.compile("^[la]_[a-z0-9_]+$");
    public static final Set<String> RESERVED_FIELDS = Set.copyOf(SystemColumns.ALL);

    private LedgerIdentifiers() {}

    public static String requireCode(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw P3Exception.invalidIdentifier(value);
        }
        return value;
    }

    public static String requireFieldCode(String value) {
        String code = requireCode(value);
        if (RESERVED_FIELDS.contains(code)) {
            throw P3Exception.reservedField(code);
        }
        return code;
    }

    public static String requirePhysicalTable(String value) {
        if (value == null || value.length() > MAX_TABLE || !TABLE.matcher(value).matches()) {
            throw P3Exception.invalidIdentifier(value);
        }
        return value;
    }

    public static String normalizeCode(String value) {
        if (value == null) {
            throw P3Exception.invalidIdentifier(null);
        }
        return requireCode(value.trim().toLowerCase(Locale.ROOT));
    }
}
