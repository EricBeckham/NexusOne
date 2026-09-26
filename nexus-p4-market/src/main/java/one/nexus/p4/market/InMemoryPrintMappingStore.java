package one.nexus.p4.market;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import org.springframework.stereotype.Component;

/** CF-R3: process-local print mapping overrides. Not durable across restarts (zero Flyway). */
@Component
public class InMemoryPrintMappingStore {
    private final Map<UUID, ProductPrintMapping> byProduct = new ConcurrentHashMap<>();

    public void save(ProductPrintMapping mapping) {
        byProduct.put(mapping.productDefinitionId(), mapping);
    }

    public Optional<ProductPrintMapping> find(UUID productDefinitionId) {
        return Optional.ofNullable(byProduct.get(productDefinitionId));
    }
}