package one.nexus.p1.domain;

import java.time.Instant;
import java.util.UUID;

public record EntitlementProjection(
        UUID tenantId,
        ProjectionSource source,
        String packCode,
        String packVersion,
        String skuCode,
        UUID subscriptionId,
        Instant projectedAt,
        EntitlementPayload payload,
        Instant updatedAt,
        long version
) {

    public EntitlementProjection {
        if (source == ProjectionSource.DEFAULT_PACK && (skuCode != null || subscriptionId != null)) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "default pack projection cannot carry SKU/subscription");
        }
        if (source == ProjectionSource.SUBSCRIPTION && subscriptionId == null) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "subscription projection requires subscription_id");
        }
    }

    public static EntitlementProjection fromDefaultPack(
            UUID tenantId, String packCode, String packVersion, EntitlementPayload payload, Instant now) {
        return new EntitlementProjection(
                tenantId,
                ProjectionSource.DEFAULT_PACK,
                packCode,
                packVersion,
                null,
                null,
                now,
                payload,
                now,
                1);
    }
}
