package one.nexus.p4.market;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.MarketModels.*;

public interface MarketRepository {
    void saveApp(AppPackage app);
    void saveVersion(AppVersion version);
    void saveSku(Sku sku);
    void saveSubscription(Subscription subscription);
    void saveInstallation(Installation installation);
    List<AppPackage> apps();
    Optional<AppPackage> app(UUID id);
    List<AppVersion> versions(UUID appId);
    Optional<AppVersion> version(UUID id);
    List<Sku> skus();
    Optional<Sku> sku(String code);
    List<Installation> installations();
    Optional<Installation> installationForCurrentTenant(UUID id);
    List<Subscription> subscriptions();
    Optional<Subscription> subscriptionForCurrentTenant(UUID id);
    List<CatalogItem> catalog();
    void saveProductDefinition(ProductDefinition definition);
    List<ProductDefinition> productDefinitions();
    Optional<ProductDefinition> productDefinition(UUID id);
    Optional<ProductDefinition> productDefinitionByCode(String code);
    Optional<UUID> publishedFormTemplateVersionByCode(String code, int version);
    Optional<UUID> publishedFormTemplateVersion(UUID id);
    void bindSkuToProductDefinition(String skuCode, UUID productDefinitionId);
    Optional<ProductDefinition> productDefinitionForSku(String skuCode);
    List<Sku> skusBoundToProductDefinition(UUID productDefinitionId);
    Optional<FormTemplateVersionSnapshot> formTemplateVersionSnapshot(UUID formTemplateVersionId);
    ProductUsageCounts productUsageCounts(UUID productDefinitionId);
    void saveInstallationProductPin(InstallationProductPin pin);
    Optional<InstallationProductPin> installationProductPin(UUID installationId);
    /** DPO-L6: step records of the current tenant's installation for one target version. */
    List<UpgradeStepRecord> upgradeStepRecords(UUID installationId, UUID versionId);
    /** DPO-L6: platform-only lookup for the controlled operator write (no tenant context). */
    Optional<Installation> installationForPlatform(UUID installationId);
    void saveUpgradeStepRecord(UpgradeStepRecord record);
    void saveActivationApplication(ActivationApplication application);
    Optional<ActivationApplication> activationApplication(UUID id);
    default Optional<ActivationApplication> activationApplicationForReview(UUID id) { return activationApplication(id); }
    default void saveApprovedSubscription(PlatformActivationCommand command, Subscription row) { throw new UnsupportedOperationException("platform activation writer required"); }
    default void saveApprovedInstallation(PlatformActivationCommand command, Installation row) { throw new UnsupportedOperationException("platform activation writer required"); }
    default void saveApprovedProductPin(PlatformActivationCommand command, InstallationProductPin row) { throw new UnsupportedOperationException("platform activation writer required"); }
    List<ActivationApplication> activationApplications();
    List<ActivationApplication> activationApplicationsForCurrentTenant();
    boolean hasActiveSubscription(UUID tenantId, String skuCode);
}
