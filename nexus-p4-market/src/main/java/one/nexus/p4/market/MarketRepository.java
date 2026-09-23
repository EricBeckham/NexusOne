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
    Optional<AppPackage> app(UUID id);
    Optional<AppVersion> version(UUID id);
    Optional<Sku> sku(String code);
    Optional<Installation> installation(UUID id);
    List<CatalogItem> catalog();
}
