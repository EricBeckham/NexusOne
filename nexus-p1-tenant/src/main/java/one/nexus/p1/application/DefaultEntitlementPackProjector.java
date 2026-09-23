package one.nexus.p1.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaMetric;

/**
 * T2-A: project the platform preset/default entitlement pack onto a tenant and
 * materialize Quota rows from that payload (not from a parallel limit source).
 * Commercial subscription overwrite stays out of scope (T4-P4b).
 */
@Service
public class DefaultEntitlementPackProjector {

    private final P1Properties properties;

    public DefaultEntitlementPackProjector(P1Properties properties) {
        this.properties = properties;
    }

    public AppliedPack project(UUID tenantId, Instant now) {
        EntitlementPayload payload = EntitlementPayload.defaultPack(
                properties.getQuota().getAppLimit(), properties.getQuota().getCustomLedgerLimit());
        EntitlementProjection projection = EntitlementProjection.fromDefaultPack(
                tenantId,
                properties.getDefaultPack().getCode(),
                properties.getDefaultPack().getVersion(),
                payload,
                now);
        List<Quota> quotas = quotasFromPayload(
                tenantId, payload, properties.getQuota().getWarnPercent(), now);
        return new AppliedPack(projection, List.copyOf(quotas));
    }

    /**
     * Derive quota rows from a runtime entitlement payload using {@link QuotaMetric#entitlementKey()}.
     */
    public static List<Quota> quotasFromPayload(
            UUID tenantId, EntitlementPayload payload, int warnPercent, Instant now) {
        List<Quota> quotas = new ArrayList<>();
        for (QuotaMetric metric : QuotaMetric.values()) {
            EntitlementPayload.EntitlementItem item = payload.entitlements().get(metric.entitlementKey());
            if (item == null || !item.enabled() || item.quota() == null || item.quota() < 0) {
                throw new DomainException(
                        ErrorCode.QUOTA_INVALID,
                        "default pack missing enabled quota for " + metric.entitlementKey());
            }
            quotas.add(new Quota(
                    tenantId,
                    metric,
                    item.quota(),
                    0,
                    warnPercent,
                    Quota.POLICY_REJECT,
                    now,
                    1));
        }
        return quotas;
    }

    public record AppliedPack(EntitlementProjection projection, List<Quota> quotas) {}
}