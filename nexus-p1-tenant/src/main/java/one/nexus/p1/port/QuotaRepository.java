package one.nexus.p1.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaMetric;

public interface QuotaRepository {

    void insert(Quota quota);

    List<Quota> listByTenant(UUID tenantId);

    Optional<Quota> find(UUID tenantId, QuotaMetric metric);

    Quota lock(UUID tenantId, QuotaMetric metric);

    void update(Quota quota);

    Optional<IdempotencyRecord> findIdempotency(UUID tenantId, QuotaMetric metric, String key);

    void insertIdempotency(IdempotencyRecord record);

    record IdempotencyRecord(
            UUID tenantId,
            QuotaMetric metric,
            String key,
            int amount,
            String outcome,
            Integer usedAfter,
            Integer limitValue,
            Instant createdAt
    ) {}
}
