package one.nexus.p1.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRepository;

@Service
public class TenantConfigService {

    private final TenantRepository tenants;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final IdGenerator ids;
    private final ClockPort clock;

    public TenantConfigService(
            TenantRepository tenants,
            IsolationGuard isolation,
            AuditPort audit,
            IdGenerator ids,
            ClockPort clock) {
        this.tenants = tenants;
        this.isolation = isolation;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TenantConfig getConfig(UUID tenantId) {
        isolation.requirePlatformContext("tenant.config.get", PlatformDuty.SYSTEM_ADMIN);
        return tenants.findConfig(tenantId).orElseThrow(() -> notFound(tenantId));
    }

    @Transactional(readOnly = true)
    public EntitlementProjection getProjection(UUID tenantId) {
        isolation.requirePlatformContext("tenant.entitlement.get", PlatformDuty.SYSTEM_ADMIN);
        return tenants.findProjection(tenantId).orElseThrow(() -> notFound(tenantId));
    }

    @Transactional
    public TenantConfig updateConfig(UUID tenantId, TenantLifecycleService.ConfigPatch patch) {
        PlatformContext platform = isolation.requirePlatformContext("tenant.config.update", PlatformDuty.SYSTEM_ADMIN);
        TenantConfig current = tenants.findConfig(tenantId).orElseThrow(() -> notFound(tenantId));
        TenantConfig next = current.update(
                patch.displayName() == null ? current.displayName() : patch.displayName(),
                patch.locale() == null ? current.locale() : patch.locale(),
                patch.timezone() == null ? current.timezone() : patch.timezone(),
                patch.dateFormat() == null ? current.dateFormat() : patch.dateFormat(),
                patch.currency() == null ? current.currency() : patch.currency(),
                patch.fiscalYearStartMonth() == null ? current.fiscalYearStartMonth() : patch.fiscalYearStartMonth(),
                patch.logoFileId() == null ? current.logoFileId() : patch.logoFileId(),
                patch.faviconFileId() == null ? current.faviconFileId() : patch.faviconFileId(),
                clock.now(),
                platform.actor().subjectId());
        tenants.updateConfig(next);
        var tenant = tenants.findById(tenantId).orElseThrow(() -> notFound(tenantId));
        audit.append(new AuditEvent(
                ids.next(),
                AuditEvent.TENANT_CONFIG_UPDATED,
                AuditEvent.VERSION,
                clock.now(),
                tenantId,
                platform.actor().subjectId(),
                platform.actorHomeTenantId(),
                true,
                platform.reason(),
                platform.traceId(),
                platform.traceId(),
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "tenant_config",
                tenantId.toString(),
                Map.of("code", tenant.code())));
        return next;
    }

    private static DomainException notFound(UUID tenantId) {
        return new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found",
                Map.of("tenant_id", tenantId.toString()));
    }
}
