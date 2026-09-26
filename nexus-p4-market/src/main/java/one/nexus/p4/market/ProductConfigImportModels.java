package one.nexus.p4.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** IM5/IM6: product-config import job shapes (in-memory; no Flyway). */
public final class ProductConfigImportModels {
    private ProductConfigImportModels() {}

    public enum ConflictStrategy { SKIP, NEW_VERSION, FAIL }

    public enum JobStatus { ACCEPTED, COMPLETED, FAILED }

    public record ImportRequest(Map<String, Object> packageBody, String conflictStrategy) {}

    public record ImportAccepted(UUID importJobId, JobStatus status) {}

    public record ResourceResult(String resourceType, String resourceKey, String outcome, String detail) {}

    public record ImportJob(
            UUID id,
            JobStatus status,
            ConflictStrategy strategy,
            String idempotencyKey,
            String checksum,
            Instant createdAt,
            Instant finishedAt,
            String error,
            List<ResourceResult> results,
            Map<String, Object> counts
    ) {
        public ImportJob {
            results = results == null ? List.of() : List.copyOf(results);
            counts = counts == null ? Map.of() : Map.copyOf(counts);
        }
    }
}