package one.nexus.p4.market;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;

public interface ProductConfigImportJobStore {
    void save(ImportJob job);
    Optional<ImportJob> find(UUID id);
    /**
     * The job for {@code key} unless it is older than the idempotency window at {@code now}. {@code now} comes from the
     * caller's clock — the one that stamped the job's {@code createdAt} — never the wall clock of the store.
     */
    Optional<ImportJob> findByIdempotencyKey(String key, Instant now);
}