package one.nexus.p4.market;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;
import org.springframework.stereotype.Component;

/** Process-local import jobs (IM5). Survives within JVM; not durable across restarts. */
@Component
public class InMemoryProductConfigImportJobStore implements ProductConfigImportJobStore {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private final Map<UUID, ImportJob> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byKey = new ConcurrentHashMap<>();

    @Override
    public void save(ImportJob job) {
        byId.put(job.id(), job);
        if (job.idempotencyKey() != null && !job.idempotencyKey().isBlank()) {
            byKey.put(job.idempotencyKey(), job.id());
        }
    }

    @Override
    public Optional<ImportJob> find(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<ImportJob> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        UUID id = byKey.get(key);
        if (id == null) return Optional.empty();
        ImportJob job = byId.get(id);
        if (job == null) return Optional.empty();
        if (job.createdAt() != null && job.createdAt().isBefore(Instant.now().minus(IDEMPOTENCY_TTL))) {
            return Optional.empty();
        }
        return Optional.of(job);
    }
}