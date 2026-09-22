package one.nexus.p1.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaDecision;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.QuotaRepository;
import one.nexus.p1.port.QuotaRepository.IdempotencyRecord;
import one.nexus.p1.port.TenantRepository;

@Service
public class QuotaService {

    private final TenantRepository tenants;
    private final QuotaRepository quotas;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final IdGenerator ids;
    private final ClockPort clock;

    public QuotaService(
            TenantRepository tenants,
            QuotaRepository quotas,
            IsolationGuard isolation,
            AuditPort audit,
            IdGenerator ids,
            ClockPort clock) {
        this.tenants = tenants;
        this.quotas = quotas;
        this.isolation = isolation;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Quota> list(UUID tenantId) {
        isolation.requireTenantOrPlatform(tenantId, "quota.list");
        requireTenant(tenantId);
        return quotas.listByTenant(tenantId);
    }

    @Transactional
    public Quota consume(UUID tenantId, QuotaMetric metric, int amount, String idempotencyKey) {
        isolation.requireTenantOrPlatform(tenantId, "quota.consume");
        IsolationSnapshot ctx = isolation.current();
        Tenant tenant = requireEnabled(tenantId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = quotas.findIdempotency(tenantId, metric, idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyRecord rec = existing.get();
                Quota current = quotas.find(tenantId, metric).orElseThrow();
                if (AuditEvent.OUTCOME_REJECTED.equals(rec.outcome())) {
                    throw exceeded(current, rec.amount(), ctx);
                }
                return current;
            }
        }
        Quota locked = quotas.lock(tenantId, metric);
        QuotaDecision decision = locked.tryConsume(amount, clock.now());
        if (decision instanceof QuotaDecision.Rejected rejected) {
            DomainException ex = exceeded(rejected.quota(), amount, ctx);
            audit.appendNewTransaction(quotaEvent(
                    AuditEvent.QUOTA_REJECTED, tenant, rejected.quota(), amount, ctx, AuditEvent.OUTCOME_REJECTED,
                    Map.of(
                            "reason_code", ErrorCode.QUOTA_EXCEEDED.code(),
                            "policy", Quota.POLICY_REJECT,
                            "idempotency_key", idempotencyKey == null ? "" : idempotencyKey)));
            throw ex;
        }
        QuotaDecision.Accepted accepted = (QuotaDecision.Accepted) decision;
        quotas.update(accepted.quota());
        remember(idempotencyKey, tenantId, metric, amount, AuditEvent.OUTCOME_ACCEPTED, accepted.quota());
        audit.append(quotaEvent(
                AuditEvent.QUOTA_CONSUMED, tenant, accepted.quota(), amount, ctx, AuditEvent.OUTCOME_ACCEPTED, Map.of()));
        return accepted.quota();
    }

    @Transactional
    public Quota release(UUID tenantId, QuotaMetric metric, int amount) {
        isolation.requireTenantOrPlatform(tenantId, "quota.release");
        IsolationSnapshot ctx = isolation.current();
        Tenant tenant = requireTenant(tenantId);
        Quota locked = quotas.lock(tenantId, metric);
        Quota next = locked.release(amount, clock.now());
        quotas.update(next);
        audit.append(quotaEvent(
                AuditEvent.QUOTA_RELEASED, tenant, next, amount, ctx, AuditEvent.OUTCOME_ACCEPTED, Map.of()));
        return next;
    }

    private void remember(
            String key, UUID tenantId, QuotaMetric metric, int amount, String outcome, Quota quota) {
        if (key == null || key.isBlank()) {
            return;
        }
        quotas.insertIdempotency(new IdempotencyRecord(
                tenantId, metric, key, amount, outcome, quota.usedValue(), quota.limitValue(), clock.now()));
    }

    private Tenant requireEnabled(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        if (!tenant.enabled()) {
            throw new DomainException(
                    ErrorCode.TENANT_INVALID_STATE,
                    "disabled tenant cannot consume quota",
                    Map.of("tenant_id", tenantId.toString(), "status", tenant.status().name()));
        }
        return tenant;
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() ->
                new DomainException(ErrorCode.TENANT_NOT_FOUND, "tenant not found",
                        Map.of("tenant_id", tenantId.toString())));
    }

    private DomainException exceeded(Quota quota, int requested, IsolationSnapshot ctx) {
        Map<String, Object> details = new LinkedHashMap<>(quota.snapshot());
        details.put("requested", requested);
        details.put("trace_id", ctx.traceId());
        return new DomainException(ErrorCode.QUOTA_EXCEEDED, "quota exceeded", details);
    }

    private AuditEvent quotaEvent(
            String type,
            Tenant tenant,
            Quota quota,
            int requested,
            IsolationSnapshot ctx,
            String outcome,
            Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>(quota.snapshot());
        details.put("requested", requested);
        details.putAll(extra);
        boolean platform = ctx.platform() != null && ctx.platform().active();
        return new AuditEvent(
                ids.next(),
                type,
                AuditEvent.VERSION,
                clock.now(),
                tenant.id(),
                ctx.actorId(),
                ctx.actor() == null ? null : ctx.actor().homeTenantId(),
                platform,
                platform ? ctx.platform().reason() : null,
                ctx.traceId(),
                ctx.traceId(),
                null,
                outcome,
                "quota",
                quota.metric().name(),
                details);
    }
}
