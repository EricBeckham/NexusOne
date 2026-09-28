package one.nexus.p3.infra.quota;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import one.nexus.p1.application.DefaultEntitlementPackProjector;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p1.port.QuotaRepository;
import one.nexus.p1.port.TenantRepository;

/** Repairs pre-existing tenants whose P1 quota row was never materialized. No quota is bypassed. */
@Component
@ConditionalOnBean(QuotaRepository.class)
public class P3QuotaRowRecovery {
    private final QuotaRepository quotas;
    private final TenantRepository tenants;
    private final DefaultEntitlementPackProjector defaultPack;
    private final JdbcClient jdbc;

    public P3QuotaRowRecovery(QuotaRepository quotas, TenantRepository tenants,
            DefaultEntitlementPackProjector defaultPack, JdbcClient jdbc) {
        this.quotas = quotas;
        this.tenants = tenants;
        this.defaultPack = defaultPack;
        this.jdbc = jdbc;
    }

    public void ensure(UUID tenantId, QuotaMetric metric) {
        if (quotas.find(tenantId, metric).isPresent()) return;

        // Subscription projections describe an installed SKU, not the tenant's base P3 limits.
        // Existing DEFAULT_PACK payloads retain their own limits; otherwise use the configured base pack.
        Instant now = Instant.now();
        List<Quota> configured = defaultPack.project(tenantId, now).quotas();
        List<Quota> rows = tenants.findProjection(tenantId)
                .filter(projection -> projection.source() == ProjectionSource.DEFAULT_PACK)
                .map(EntitlementProjection::payload)
                .map(payload -> DefaultEntitlementPackProjector.quotasFromPayload(
                        tenantId, payload, configured.getFirst().warnPercent(), now))
                .orElse(configured);
        Quota baseline = rows.stream()
                .filter(row -> row.metric() == metric)
                .findFirst().orElseThrow();
        int used = existingCount(tenantId, metric);
        jdbc.sql("""
                INSERT INTO platform.quota (tenant_id, metric, limit_value, used_value,
                    warn_percent, over_limit_policy, updated_at, version)
                VALUES (:tenant, :metric, :limit, :used, :warn, 'REJECT', :now, 1)
                ON CONFLICT (tenant_id, metric) DO NOTHING
                """)
                .param("tenant", tenantId).param("metric", metric.name())
                .param("limit", baseline.limitValue()).param("used", used)
                .param("warn", baseline.warnPercent()).param("now", Timestamp.from(now)).update();
    }

    private int existingCount(UUID tenantId, QuotaMetric metric) {
        String sql = metric == QuotaMetric.APP
                ? """
                  SELECT count(*) FROM business_meta.application
                  WHERE tenant_id = :tenant AND kind = 'CUSTOM' AND deleted_at IS NULL
                  """
                : """
                  SELECT count(*) FROM business_meta.ledger l
                  JOIN business_meta.application a ON a.id = l.application_id
                  WHERE l.tenant_id = :tenant AND l.scope = 'TENANT' AND l.deleted_at IS NULL
                    AND a.kind = 'CUSTOM' AND a.deleted_at IS NULL
                  """;
        return jdbc.sql(sql).param("tenant", tenantId).query(Integer.class).single();
    }
}
