package one.nexus.p1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import one.nexus.p1.application.DefaultEntitlementPackProjector;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaDecision;
import one.nexus.p1.domain.QuotaMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T2-A acceptance: preset/default entitlement pack projects onto a tenant and
 * Quota limits reflect that payload. Commercial billing/coupons and
 * subscription overwrite (T4-P4b) are out of scope.
 */
class T2EntitlementQuotaProjectionTest {

    private static final Instant NOW = Instant.parse("2026-09-23T09:00:00Z");
    private static final UUID TENANT = UUID.fromString("01900000-0000-7000-8000-00000000a2a1");

    private P1Properties properties;
    private DefaultEntitlementPackProjector projector;

    @BeforeEach
    void setUp() {
        properties = new P1Properties();
        properties.getDefaultPack().setCode("nexus.default.m0");
        properties.getDefaultPack().setVersion("0.1.0");
        properties.getQuota().setAppLimit(5);
        properties.getQuota().setCustomLedgerLimit(10);
        properties.getQuota().setWarnPercent(80);
        projector = new DefaultEntitlementPackProjector(properties);
    }

    @Test
    void presetPackProjectsOntoTenantWithMatchingQuotas() {
        DefaultEntitlementPackProjector.AppliedPack applied = projector.project(TENANT, NOW);

        EntitlementProjection projection = applied.projection();
        assertThat(projection.tenantId()).isEqualTo(TENANT);
        assertThat(projection.source()).isEqualTo(ProjectionSource.DEFAULT_PACK);
        assertThat(projection.packCode()).isEqualTo("nexus.default.m0");
        assertThat(projection.packVersion()).isEqualTo("0.1.0");
        assertThat(projection.skuCode()).isNull();
        assertThat(projection.subscriptionId()).isNull();
        assertThat(projection.payload().schema()).isEqualTo(EntitlementPayload.SCHEMA_V1);
        assertThat(projection.payload().entitlements()).containsOnlyKeys("apps", "custom_ledgers");
        assertThat(projection.payload().entitlements().get("apps").quota()).isEqualTo(5);
        assertThat(projection.payload().entitlements().get("custom_ledgers").quota()).isEqualTo(10);

        Map<QuotaMetric, Quota> byMetric = applied.quotas().stream()
                .collect(Collectors.toMap(Quota::metric, Function.identity()));
        assertThat(byMetric).containsOnlyKeys(QuotaMetric.APP, QuotaMetric.CUSTOM_LEDGER);

        assertQuotaMatchesPayload(byMetric.get(QuotaMetric.APP), projection.payload(), QuotaMetric.APP);
        assertQuotaMatchesPayload(byMetric.get(QuotaMetric.CUSTOM_LEDGER), projection.payload(), QuotaMetric.CUSTOM_LEDGER);

        Quota app = byMetric.get(QuotaMetric.APP);
        QuotaDecision accepted = app.tryConsume(5, NOW);
        assertThat(accepted).isInstanceOf(QuotaDecision.Accepted.class);
        QuotaDecision rejected = ((QuotaDecision.Accepted) accepted).quota().tryConsume(1, NOW);
        assertThat(rejected).isInstanceOf(QuotaDecision.Rejected.class);
    }

    @Test
    void quotasAreMaterializedFromPayloadNotParallelLimits() {
        EntitlementPayload custom = EntitlementPayload.defaultPack(3, 7);
        List<Quota> quotas = DefaultEntitlementPackProjector.quotasFromPayload(TENANT, custom, 80, NOW);

        Map<QuotaMetric, Quota> byMetric = quotas.stream()
                .collect(Collectors.toMap(Quota::metric, Function.identity()));
        assertThat(byMetric.get(QuotaMetric.APP).limitValue()).isEqualTo(3);
        assertThat(byMetric.get(QuotaMetric.CUSTOM_LEDGER).limitValue()).isEqualTo(7);
        assertThat(byMetric.get(QuotaMetric.APP).usedValue()).isZero();
        assertThat(byMetric.get(QuotaMetric.CUSTOM_LEDGER).overLimitPolicy()).isEqualTo(Quota.POLICY_REJECT);
    }

    @Test
    void missingPackEntitlementFailsClosed() {
        EntitlementPayload incomplete = new EntitlementPayload(
                EntitlementPayload.SCHEMA_V1,
                Map.of("apps", new EntitlementPayload.EntitlementItem(true, 5, Map.of())));

        assertThatThrownBy(() -> DefaultEntitlementPackProjector.quotasFromPayload(TENANT, incomplete, 80, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).errorCode()).isEqualTo(ErrorCode.QUOTA_INVALID));
    }

    private static void assertQuotaMatchesPayload(Quota quota, EntitlementPayload payload, QuotaMetric metric) {
        EntitlementPayload.EntitlementItem item = payload.entitlements().get(metric.entitlementKey());
        assertThat(item).isNotNull();
        assertThat(quota.tenantId()).isEqualTo(TENANT);
        assertThat(quota.limitValue()).isEqualTo(item.quota());
        assertThat(quota.usedValue()).isZero();
        assertThat(quota.warnPercent()).isEqualTo(80);
        assertThat(quota.overLimitPolicy()).isEqualTo(Quota.POLICY_REJECT);
    }
}