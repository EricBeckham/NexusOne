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
    Optional<UUID> publishedFormTemplateVersion(UUID id);
    void bindSkuToProductDefinition(String skuCode, UUID productDefinitionId);
    Optional<ProductDefinition> productDefinitionForSku(String skuCode);
    List<Sku> skusBoundToProductDefinition(UUID productDefinitionId);
    void saveInstallationProductPin(InstallationProductPin pin);
    Optional<InstallationProductPin> installationProductPin(UUID installationId);
}
