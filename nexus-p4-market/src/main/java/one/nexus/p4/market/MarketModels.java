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
    public record InstallationProductPin(UUID installationId, UUID productDefinitionId,
            UUID formTemplateVersionId, String processDefinitionKey, Integer processDefinitionVersion,
            Instant pinnedAt) {}
    public record CatalogItem(AppPackage app, AppVersion version, java.util.List<CatalogSku> skus) {}

    /**
     * XB7: one upgrade fact. {@code value == null} means nothing declares or records it (shown as 暂无);
     * {@code note} says why in plain words. Never a claim that something was checked when it was not.
     */
    public record UpgradeFact(String key, String label, String value, String note) {}
    public record UpgradeFacts(UUID installationId, UUID currentVersionId, java.util.List<UpgradeFact> facts) {
        public UpgradeFacts {
            facts = java.util.List.copyOf(facts);
        }

        /** What the code can honestly say today: versions declare no compatibility/schema/migration data. */
        public static UpgradeFacts current(Installation installation) {
            return new UpgradeFacts(installation.id(), installation.versionId(), java.util.List.of(
                    new UpgradeFact("compatibility", "兼容范围", null, "应用版本没有声明兼容范围。"),
                    new UpgradeFact("schema_change", "Schema 变更", null, "应用版本没有声明结构变更。"),
                    new UpgradeFact("data_migration", "数据迁移", null, "升级只切换版本，不执行数据迁移，也没有迁移记录。"),
                    new UpgradeFact("rollback", "回滚能力", "仅切换版本",
                            "可以切回同一应用的已发布版本；数据不会随之回滚。")));
        }
    }

    /** XB6: status words shared with the web stepper (XB4). DONE/ACTIVE/FAILED only ever come from a record. */
    public enum UpgradeStepStatus { DONE, ACTIVE, FAILED, UNKNOWN }
    public record UpgradeStep(String key, String label, UpgradeStepStatus status) {}
    /**
     * XB6: the §28.6 upgrade steps for one installation. {@code recorded=false} means no step was ever
     * persisted — upgrade today only switches the version — so every step is UNKNOWN, never COMPLETED/DONE.
     */
    public record UpgradeSteps(UUID installationId, boolean recorded, String note, java.util.List<UpgradeStep> steps) {
        public static final java.util.List<String[]> CATALOG = java.util.List.of(
                new String[] {"precheck", "预检"},
                new String[] {"dependency", "依赖检查"},
                new String[] {"schema_migration", "结构扩展与迁移"},
                new String[] {"config", "配置"},
                new String[] {"switch", "切换"},
                new String[] {"verify", "验证"});
        public static final String UNRECORDED_NOTE = "升级步骤没有被记录：当前升级只切换版本，不执行也不记录预检到验证的各个步骤。";

        public UpgradeSteps {
            steps = java.util.List.copyOf(steps);
        }

        public static UpgradeSteps unrecorded(UUID installationId) {
            return new UpgradeSteps(installationId, false, UNRECORDED_NOTE, CATALOG.stream()
                    .map(step -> new UpgradeStep(step[0], step[1], UpgradeStepStatus.UNKNOWN))
                    .toList());
        }
    }
    /**
     * PC6: one step of the product configuration wizard. DONE only when the stored data proves it;
     * NOT_READY when it provably is not; UNKNOWN when the check itself could not be made.
     */
    public enum ReadinessStatus { DONE, NOT_READY, UNKNOWN }
    public record ConfigReadinessStep(String key, String label, ReadinessStatus status, String detail) {}
    public record ProductConfigReadiness(UUID productDefinitionId, String definitionStatus, boolean complete,
            java.util.List<ConfigReadinessStep> steps) {
        public ProductConfigReadiness {
            steps = java.util.List.copyOf(steps);
        }

        public static ProductConfigReadiness of(ProductDefinition definition, java.util.List<ConfigReadinessStep> steps) {
            boolean complete = !steps.isEmpty() && steps.stream().allMatch(step -> step.status() == ReadinessStatus.DONE);
            return new ProductConfigReadiness(definition.id(), definition.status(), complete, steps);
        }
    }
    /** Public catalog contract: subscription identity is available directly on each SKU entry. */
    public record CatalogSku(String code, String name, Map<String, EntitlementSpec> entitlements,
            ProductDefinition productDefinition) {
        public CatalogSku {
            entitlements = entitlements == null ? Map.of() : Map.copyOf(entitlements);
        }
    }
    public record ProductDefinition(UUID id, String code, String name, String description,
            UUID formTemplateVersionId, String processDefinitionKey, Integer processDefinitionVersion,
            Map<String, Object> defaultPermissionPack, String status, Instant createdAt, Instant updatedAt,
            long version) {
        public ProductDefinition {
            defaultPermissionPack = defaultPermissionPack == null ? Map.of() : Map.copyOf(defaultPermissionPack);
        }
    }
    public record ProductDefinitionWrite(String code, String name, String description,
            UUID formTemplateVersionId, String processDefinitionKey, Integer processDefinitionVersion,
            Map<String, Object> defaultPermissionPack) {
        public ProductDefinitionWrite {
            defaultPermissionPack = defaultPermissionPack == null ? Map.of() : Map.copyOf(defaultPermissionPack);
        }
    }
}
