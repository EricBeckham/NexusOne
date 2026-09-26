package one.nexus.p1.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import one.nexus.p1.application.TenantLifecycleService;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.domain.TenantRegistrationApplication;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitResult;

public final class ApiModels {

    private ApiModels() {
    }

    public record CreateTenantRequest(
            @NotBlank String name,
            String code,
            String legalName,
            @NotNull @Valid AdminRequest admin,
            ConfigRequest config
    ) {}

    public record AdminRequest(
            @NotBlank String displayName,
            @NotBlank @Email String email
    ) {}

    public record ConfigRequest(
            String displayName,
            String locale,
            String timezone,
            String dateFormat,
            String currency,
            Integer fiscalYearStartMonth,
            UUID logoFileId,
            UUID faviconFileId
    ) {
        public TenantLifecycleService.ConfigPatch toPatch() {
            return new TenantLifecycleService.ConfigPatch(
                    displayName, locale, timezone, dateFormat, currency,
                    fiscalYearStartMonth, logoFileId, faviconFileId);
        }
    }

    public record ConfirmRequest(boolean confirm, String reason) {}

    public record ApplyTenantRegistrationRequest(
            @NotBlank String orgName,
            @NotBlank String adminDisplayName,
            @NotBlank @Email String adminEmail,
            String contactNote
    ) {}

    public record ReviewRegistrationRequest(String note) {}

    public record TenantRegistrationView(
            UUID id,
            String orgName,
            String adminDisplayName,
            String adminEmail,
            String contactNote,
            String status,
            String reviewNote,
            Instant reviewedAt,
            UUID reviewedBy,
            UUID resultingTenantId,
            Instant createdAt
    ) {
        public static TenantRegistrationView of(TenantRegistrationApplication application) {
            return new TenantRegistrationView(
                    application.id(),
                    application.orgName(),
                    application.adminDisplayName(),
                    application.adminEmail(),
                    application.contactNote(),
                    application.status().name(),
                    application.reviewNote(),
                    application.reviewedAt(),
                    application.reviewedBy(),
                    application.resultingTenantId(),
                    application.createdAt());
        }
    }

    public record ConsumeQuotaRequest(
            @NotBlank String metric,
            int amount,
            String idempotencyKey
    ) {}

    public record ErrorBody(String code, String message, String traceId, Map<String, Object> details) {}

    public record TenantView(
            UUID id,
            String code,
            String name,
            String kind,
            String status,
            String legalName,
            long version
    ) {
        public static TenantView of(Tenant tenant) {
            return new TenantView(
                    tenant.id(), tenant.code(), tenant.name(), tenant.kind().name(),
                    tenant.status().name(), tenant.legalName(), tenant.version());
        }
    }

    public record CreatedTenantView(
            TenantView tenant,
            TenantConfig config,
            EntitlementProjection entitlement,
            AdminInitView adminInit
    ) {}

    public record AdminInitView(UUID requestId, String status, String message) {
        public static AdminInitView of(AdminInitResult result) {
            return new AdminInitView(result.requestId(), result.status().name(), result.message());
        }
    }

    public record QuotaView(
            UUID tenantId,
            String metric,
            int limit,
            int used,
            int warnPercent,
            String overLimitPolicy,
            boolean warning
    ) {
        public static QuotaView of(Quota quota) {
            return new QuotaView(
                    quota.tenantId(),
                    quota.metric().name(),
                    quota.limitValue(),
                    quota.usedValue(),
                    quota.warnPercent(),
                    quota.overLimitPolicy(),
                    quota.warning());
        }
    }
}
