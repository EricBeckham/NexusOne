package one.nexus.p1.domain;

import java.util.Locale;

public enum QuotaMetric {
    APP,
    CUSTOM_LEDGER;

    public String entitlementKey() {
        return this == APP ? "apps" : "custom_ledgers";
    }

    public static QuotaMetric fromApi(String value) {
        if (value == null) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "metric is required");
        }
        try {
            return QuotaMetric.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "unknown quota metric: " + value);
        }
    }
}
