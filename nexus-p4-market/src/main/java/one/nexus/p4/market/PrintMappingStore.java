package one.nexus.p4.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.PrintMappingModels.PrintMappingRevision;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;

/** DPO-L3: where per-product print mappings live. Production uses JDBC; the in-memory store is for unit tests. */
public interface PrintMappingStore {
    Optional<ProductPrintMapping> find(UUID productDefinitionId);

    /**
     * Writes {@code next} only if the stored revision is still {@code expectedCurrent} (0 = nothing stored yet)
     * and appends one history row. Returns false when someone else saved first; nothing is written then.
     */
    boolean saveIfRevision(ProductPrintMapping next, long expectedCurrent, UUID savedBy, Instant savedAt);

    /** Newest first, at most {@code limit} rows. */
    List<PrintMappingRevision> history(UUID productDefinitionId, int limit);

    /** Wire {@code source} and note for mappings this store returns. */
    String source();

    String note();
}
