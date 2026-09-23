package one.nexus.p4.market;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MarketModels {
    private MarketModels() {}

    public enum VersionStatus { DRAFT, IN_REVIEW, APPROVED, PUBLISHED, UNPUBLISHED }
    public enum InstallationStatus { INSTALLED, UNINSTALLED }
    public enum SubscriptionStatus { ACTIVE, CANCELLED }

    public record AppPackage(UUID id, String code, String name, String summary, Instant createdAt) {}
    public record AppVersion(UUID id, UUID appId, String version, VersionStatus status, String releaseNotes,
                             Instant createdAt, Instant publishedAt) {}
    public record Sku(String code, UUID appId, String name, Map<String, EntitlementSpec> entitlements) {}
    public record EntitlementSpec(boolean enabled, Integer quota, Map<String, Object> attributes) {
        public EntitlementSpec {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
    public record Subscription(UUID id, UUID tenantId, String skuCode, SubscriptionStatus status,
                               Instant startedAt, Instant endedAt) {}
    public record Installation(UUID id, UUID tenantId, UUID appId, UUID versionId,
                               InstallationStatus status, Instant installedAt, Instant updatedAt) {}
    public record CatalogItem(AppPackage app, AppVersion version, java.util.List<Sku> skus) {}
}
