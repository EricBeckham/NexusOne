package one.nexus.p4.market;

import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;

public interface ProductConfigImportJobStore {
    void save(ImportJob job);
    Optional<ImportJob> find(UUID id);
    Optional<ImportJob> findByIdempotencyKey(String key);
}