package one.nexus.p3.domain;

import java.util.Locale;
import one.nexus.isolation.api.TableScope;

public enum LedgerScope {
    TENANT,
    APP_SHARED;

    public TableScope tableScope() {
        return this == TENANT ? TableScope.TENANT : TableScope.APP_SHARED;
    }

    public String registryScope() {
        return this == TENANT ? "tenant" : "app_shared";
    }

    public static LedgerScope fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return TENANT;
        }
        try {
            return LedgerScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw P3Exception.invalidIdentifier(raw);
        }
    }
}
