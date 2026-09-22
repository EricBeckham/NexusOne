package one.nexus.p3.domain;

/** Physical table names. Tenant custom: {@code l_*}; app-shared: {@code a_*}. */
public final class TableNames {
    private TableNames() {}

    public static String custom(String tenantCode, String ledgerCode) {
        return LedgerIdentifiers.requirePhysicalTable(
                "l_" + LedgerIdentifiers.requireCode(tenantCode) + "_" + LedgerIdentifiers.requireCode(ledgerCode));
    }

    public static String shared(String appCode, String ledgerCode) {
        return LedgerIdentifiers.requirePhysicalTable(
                "a_" + LedgerIdentifiers.requireCode(appCode) + "_" + LedgerIdentifiers.requireCode(ledgerCode));
    }

    public static String of(LedgerScope scope, String tenantCode, String appCode, String ledgerCode) {
        return scope == LedgerScope.APP_SHARED ? shared(appCode, ledgerCode) : custom(tenantCode, ledgerCode);
    }
}
