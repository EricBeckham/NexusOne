package one.nexus.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Copies aged archive rows (and archive-eligible hot rows that remain after
 * the 365-day same-PG purge) into {@link AuditObjectStore}. Never mutates the
 * append-only hot chain.
 */
public final class AuditColdArchive implements AuditColdArchivePort {
    private final EligibleSource source;
    private final AuditObjectStore store;

    public AuditColdArchive(AuditPort hot, AuditArchivePort archive, AuditObjectStore store) {
        this(fromPorts(hot, archive), store);
    }

    public static AuditColdArchive jdbc(JdbcAuditArchivePort archive, AuditObjectStore store) {
        Objects.requireNonNull(archive, "archive");
        return new AuditColdArchive(archive::findEligibleForCold, store);
    }

    public AuditColdArchive(EligibleSource source, AuditObjectStore store) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public ColdArchiveRunResult copyEligible(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant cutoff = AuditRetentionPolicy.hotCutoff(now);
        List<ChainedAuditEvent> eligible = source.eligible(now);
        int written = 0;
        int skipped = 0;
        List<String> keys = new ArrayList<>();
        for (ChainedAuditEvent linked : eligible) {
            String key = AuditColdObjectKeys.objectKey(linked);
            if (store.exists(key)) {
                skipped++;
                continue;
            }
            store.put(key, AuditColdObjectCodec.encode(linked));
            keys.add(key);
            written++;
        }
        return new ColdArchiveRunResult(written, skipped, eligible.size(), cutoff, keys);
    }

    @Override
    public List<String> list(String prefix) {
        return store.list(prefix == null ? "" : prefix);
    }

    @Override
    public List<String> listTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return store.list(AuditColdObjectKeys.tenantPrefix(tenantId));
    }

    @Override
    public List<String> listPlatform() {
        return store.list(AuditColdObjectKeys.platformPrefix());
    }

    @Override
    public ChainedAuditEvent restore(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
        return AuditColdObjectCodec.decode(store.get(objectKey));
    }

    @Override
    public List<ChainedAuditEvent> restoreByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        List<ChainedAuditEvent> out = new ArrayList<>();
        for (String key : store.list("")) {
            if (!key.contains(AuditColdObjectKeys.RELATIVE_PREFIX)) {
                continue;
            }
            ChainedAuditEvent linked = AuditColdObjectCodec.decode(store.get(key));
            if (traceId.equals(linked.event().traceId())) {
                out.add(linked);
            }
        }
        out.sort(Comparator.comparingLong(ChainedAuditEvent::chainIndex));
        return List.copyOf(out);
    }

    public static EligibleSource fromPorts(AuditPort hot, AuditArchivePort archive) {
        Objects.requireNonNull(hot, "hot");
        Objects.requireNonNull(archive, "archive");
        return now -> eligible(archive.findArchived(), hot.chain(), now);
    }

    static List<ChainedAuditEvent> eligible(
            List<ChainedAuditEvent> archived, List<ChainedAuditEvent> hot, Instant now) {
        Instant cutoff = AuditRetentionPolicy.hotCutoff(now);
        Map<UUID, ChainedAuditEvent> byId = new LinkedHashMap<>();
        for (ChainedAuditEvent linked : archived) {
            byId.put(linked.event().eventId(), linked);
        }
        for (ChainedAuditEvent linked : hot) {
            UUID id = linked.event().eventId();
            if (byId.containsKey(id) || linked.event().occurredAt().isBefore(cutoff)) {
                byId.putIfAbsent(id, linked);
            }
        }
        return List.copyOf(byId.values());
    }

    @FunctionalInterface
    public interface EligibleSource {
        List<ChainedAuditEvent> eligible(Instant now);
    }
}
