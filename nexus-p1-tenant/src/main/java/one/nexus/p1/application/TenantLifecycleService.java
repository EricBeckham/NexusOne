package one.nexus.p1.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantCode;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.domain.TenantKind;
import one.nexus.p1.domain.TenantStatus;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.QuotaRepository;
import one.nexus.p1.port.TenantAdminInitializer;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitCommand;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitResult;
import one.nexus.p1.port.TenantRepository;

@Service
public class TenantLifecycleService {

    private final TenantRepository tenants;
    private final QuotaRepository quotas;
    private final DefaultEntitlementPackProjector packProjector;
    private final TenantAdminInitializer adminInitializer;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final IdGenerator ids;
    private final ClockPort clock;
    public TenantLifecycleService(
            TenantRepository tenants,
            QuotaRepository quotas,
            DefaultEntitlementPackProjector packProjector,
            TenantAdminInitializer adminInitializer,
            IsolationGuard isolation,
            AuditPort audit,
            IdGenerator ids,
            ClockPort clock) {
        this.tenants = tenants;
        this.quotas = quotas;
        this.packProjector = packProjector;
        this.adminInitializer = adminInitializer;
        this.isolation = isolation;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public CreatedTenant create(CreateTenantCommand command) {
        PlatformContext platform = isolation.requirePlatformContext("tenant.create", PlatformDuty.SYSTEM_ADMIN);
        Instant now = clock.now();
        Actor actor = platform.actor();
        String code = allocateCode(command.code(), command.name());
        UUID id = ids.next();
        Tenant tenant = new Tenant(
                id,
                code,
                command.name().trim(),
                TenantKind.BUSINESS,
                TenantStatus.ENABLED,
                command.legalName(),
                now,
                actor.subjectId(),
                now,
                actor.subjectId(),
                null,
                1);
        TenantConfig config = command.config() == null
                ? TenantConfig.defaults(id, tenant.name(), now, actor.subjectId())
                : command.config().toConfig(id, tenant.name(), now, actor.subjectId());
        DefaultEntitlementPackProjector.AppliedPack applied = packProjector.project(id, now);
        EntitlementProjection projection = applied.projection();
        tenants.insert(tenant, config, projection);
        for (Quota quota : applied.quotas()) {
            quotas.insert(quota);
        }

        AdminInitResult init = adminInitializer.initialize(new AdminInitCommand(
                id, code, command.adminDisplayName(), command.adminEmail(),
                actor, platform.traceId(), platform.traceId()));
        if (init.status() == AdminInitResult.Status.FAILED) {
            throw new DomainException(ErrorCode.ADMIN_INIT_FAILED, init.message());
        }
        tenants.insertAdminInit(
                init.requestId(), id, command.adminDisplayName(), command.adminEmail(),
                init.status(), init.message(), actor.subjectId(), platform.traceId());

        audit.append(event(AuditEvent.TENANT_CREATED, tenant, platform, AuditEvent.OUTCOME_ACCEPTED,
                Map.of("code", code, "admin_init_status", init.status().name())));
        audit.append(event(AuditEvent.ADMIN_INIT_ACCEPTED, tenant, platform, AuditEvent.OUTCOME_ACCEPTED,
                Map.of("request_id", init.requestId().toString(), "status", init.status().name())));
        return new CreatedTenant(tenant, config, projection, init);
    }

    @Transactional
    public Tenant enable(UUID tenantId, boolean confirm) {
        PlatformContext platform = isolation.requirePlatformContext("tenant.enable", PlatformDuty.SYSTEM_ADMIN);
        requireConfirm(confirm);
        Tenant current = requireTenant(tenantId);
        Tenant next = current.enable(clock.now(), platform.actor().subjectId());
        if (next.version() != current.version()) {
            tenants.update(next);
            audit.append(event(AuditEvent.TENANT_ENABLED, next, platform, AuditEvent.OUTCOME_ACCEPTED, Map.of()));
        }
        return next;
    }

    @Transactional
    public Tenant disable(UUID tenantId, boolean confirm, String reason) {
        PlatformContext platform = isolation.requirePlatformContext("tenant.disable", PlatformDuty.SYSTEM_ADMIN);
        requireConfirm(confirm);
        Tenant current = requireTenant(tenantId);
        try {
            Tenant next = current.disable(clock.now(), platform.actor().subjectId());
            if (next.version() != current.version()) {
                tenants.update(next);
                Map<String, Object> details = new LinkedHashMap<>();
                if (reason != null) {
                    details.put("reason", reason);
                }
                audit.append(event(AuditEvent.TENANT_DISABLED, next, platform, AuditEvent.OUTCOME_ACCEPTED, details));
            }
            return next;
        } catch (DomainException ex) {
            if (ex.errorCode() == ErrorCode.TENANT_PLATFORM_PROTECTED) {
                Map<String, Object> details = new LinkedHashMap<>(ex.details());
                details.put("reason_code", ex.errorCode().code());
                audit.appendNewTransaction(event(
                        AuditEvent.TENANT_DISABLE_REJECTED, current, platform,
                        AuditEvent.OUTCOME_REJECTED, details));
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        isolation.requirePlatformContext("tenant.get", PlatformDuty.SYSTEM_ADMIN);
        return requireTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Tenant> list() {
        isolation.requirePlatformContext("tenant.list", PlatformDuty.SYSTEM_ADMIN);
        return tenants.listAll();
    }

    private String allocateCode(String requested, String name) {
        if (requested != null && !requested.isBlank()) {
            String code = TenantCode.requireBusiness(requested.trim());
            if (tenants.codeExists(code)) {
                throw new DomainException(ErrorCode.TENANT_CODE_CONFLICT, "tenant code already exists",
                        Map.of("code", code));
            }
            return code;
        }
        String base = TenantCode.slugFromName(name);
        if (TenantCode.reserved(base)) {
            base = "t_" + base;
        }
        String candidate = base;
        int n = 0;
        while (tenants.codeExists(candidate) || !TenantCode.isWellFormed(candidate)) {
            n++;
            String suffix = Integer.toString(n, 36);
            String truncated = base.substring(0, Math.min(base.length(), MAX_CODE_WITHOUT_SUFFIX - suffix.length() - 1));
            candidate = truncated + "_" + suffix;
        }
        return TenantCode.requireBusiness(candidate);
    }

    private static final int MAX_CODE_WITHOUT_SUFFIX = 24;

    private Tenant requireTenant(UUID id) {
        return tenants.findById(id).orElseThrow(() ->
                new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found", Map.of("tenant_id", id.toString())));
    }

    private static void requireConfirm(boolean confirm) {
        if (!confirm) {
            throw new DomainException(ErrorCode.TENANT_CONFIRM_REQUIRED, "high-risk tenant lifecycle requires confirm=true");
        }
    }

    private AuditEvent event(
            String type, Tenant tenant, PlatformContext platform, String outcome, Map<String, Object> details) {
        return new AuditEvent(
                ids.next(),
                type,
                AuditEvent.VERSION,
                clock.now(),
                tenant.id(),
                platform.actor().subjectId(),
                platform.actorHomeTenantId(),
                true,
                platform.reason(),
                platform.traceId(),
                platform.traceId(),
                null,
                outcome,
                "tenant",
                tenant.id().toString(),
                details);
    }

    public record CreateTenantCommand(
            String name,
            String code,
            String legalName,
            String adminDisplayName,
            String adminEmail,
            ConfigPatch config
    ) {}

    public record ConfigPatch(
            String displayName,
            String locale,
            String timezone,
            String dateFormat,
            String currency,
            Integer fiscalYearStartMonth,
            UUID logoFileId,
            UUID faviconFileId
    ) {
        TenantConfig toConfig(UUID tenantId, String tenantName, Instant now, UUID actorId) {
            TenantConfig defaults = TenantConfig.defaults(tenantId, tenantName, now, actorId);
            return new TenantConfig(
                    tenantId,
                    displayName == null || displayName.isBlank() ? tenantName : displayName,
                    locale == null ? defaults.locale() : locale,
                    timezone == null ? defaults.timezone() : timezone,
                    dateFormat == null ? defaults.dateFormat() : dateFormat,
                    currency == null ? defaults.currency() : currency,
                    fiscalYearStartMonth == null ? defaults.fiscalYearStartMonth() : fiscalYearStartMonth,
                    logoFileId,
                    faviconFileId,
                    now,
                    actorId,
                    1);
        }
    }

    public record CreatedTenant(
            Tenant tenant,
            TenantConfig config,
            EntitlementProjection projection,
            AdminInitResult adminInit
    ) {}
}
