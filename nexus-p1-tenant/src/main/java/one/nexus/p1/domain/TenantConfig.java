package one.nexus.p1.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

public record TenantConfig(
        UUID tenantId,
        String displayName,
        String locale,
        String timezone,
        String dateFormat,
        String currency,
        int fiscalYearStartMonth,
        UUID logoFileId,
        UUID faviconFileId,
        Instant updatedAt,
        UUID updatedBy,
        long version
) {

    public static TenantConfig defaults(UUID tenantId, String displayName, Instant now, UUID actorId) {
        return new TenantConfig(
                tenantId,
                displayName,
                "zh-CN",
                "Asia/Shanghai",
                "yyyy-MM-dd",
                "CNY",
                1,
                null,
                null,
                now,
                actorId,
                1);
    }

    public TenantConfig {
        if (displayName == null || displayName.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "display name is required");
        }
        if (fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "fiscal year start month must be 1-12");
        }
        try {
            Locale.forLanguageTag(locale.replace('_', '-'));
            ZoneId.of(timezone);
            Currency.getInstance(currency);
        } catch (RuntimeException ex) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "invalid locale, timezone, or currency");
        }
        if (dateFormat == null || dateFormat.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "date format is required");
        }
    }

    public TenantConfig update(
            String displayName,
            String locale,
            String timezone,
            String dateFormat,
            String currency,
            int fiscalYearStartMonth,
            UUID logoFileId,
            UUID faviconFileId,
            Instant now,
            UUID actorId) {
        return new TenantConfig(
                tenantId,
                displayName,
                locale,
                timezone,
                dateFormat,
                currency,
                fiscalYearStartMonth,
                logoFileId,
                faviconFileId,
                now,
                actorId,
                version + 1);
    }
}
