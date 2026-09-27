package one.nexus.p4.market;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.p4.market.PrintMappingModels.PrintMappingRevision;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;

/**
 * CF-R3: process-local print mappings. Since DPO-L3 the application uses {@link
 * one.nexus.p4.market.infra.jdbc.JdbcPrintMappingStore}; this class is not a bean and only backs unit tests.
 */
public class InMemoryPrintMappingStore implements PrintMappingStore {
    private final Map<UUID, ProductPrintMapping> byProduct = new ConcurrentHashMap<>();
    private final Map<UUID, List<PrintMappingRevision>> revisions = new ConcurrentHashMap<>();

    @Override
    public Optional<ProductPrintMapping> find(UUID productDefinitionId) {
        return Optional.ofNullable(byProduct.get(productDefinitionId));
    }

    @Override
    public synchronized boolean saveIfRevision(ProductPrintMapping next, long expectedCurrent, UUID savedBy,
            Instant savedAt) {
        long current = find(next.productDefinitionId()).map(ProductPrintMapping::revision).orElse(0L);
        if (current != expectedCurrent) return false;
        byProduct.put(next.productDefinitionId(), next);
        revisions.computeIfAbsent(next.productDefinitionId(), id -> new ArrayList<>()).add(0,
                new PrintMappingRevision(next.productDefinitionId(), next.revision(), next.templateCode(),
                        next.paper(), next.orientation(), next.mappings(), savedAt, savedBy));
        return true;
    }

    @Override
    public List<PrintMappingRevision> history(UUID productDefinitionId, int limit) {
        return revisions.getOrDefault(productDefinitionId, List.of()).stream().limit(limit).toList();
    }

    @Override
    public String source() {
        return ProductPrintMapping.SOURCE_IN_MEMORY;
    }

    @Override
    public String note() {
        return ProductPrintMapping.NOTE_IN_MEMORY;
    }
}
