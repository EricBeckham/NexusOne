package one.nexus.p1.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Quota(
        UUID tenantId,
        QuotaMetric metric,
        int limitValue,
        int usedValue,
        int warnPercent,
        String overLimitPolicy,
        Instant updatedAt,
        long version
) {

    public static final String POLICY_REJECT = "REJECT";

    public Quota {
        if (limitValue < 0 || usedValue < 0) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "quota values must be >= 0");
        }
        if (!POLICY_REJECT.equals(overLimitPolicy)) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "M0 only supports over-limit policy REJECT");
        }
    }

    public boolean warning() {
        return limitValue > 0 && usedValue * 100 >= limitValue * warnPercent;
    }

    public QuotaDecision tryConsume(int amount, Instant now) {
        if (amount <= 0) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "consume amount must be > 0");
        }
        long next = (long) usedValue + amount;
        if (next > limitValue) {
            return new QuotaDecision.Rejected(this, amount, "quota exceeded");
        }
        Quota updated = new Quota(
                tenantId, metric, limitValue, (int) next, warnPercent, overLimitPolicy, now, version + 1);
        return new QuotaDecision.Accepted(updated, amount);
    }

    public Quota release(int amount, Instant now) {
        if (amount <= 0) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "release amount must be > 0");
        }
        int next = Math.max(0, usedValue - amount);
        return new Quota(tenantId, metric, limitValue, next, warnPercent, overLimitPolicy, now, version + 1);
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "metric", metric.name(),
                "limit", limitValue,
                "used", usedValue,
                "warn_percent", warnPercent,
                "policy", overLimitPolicy,
                "warning", warning());
    }
}
