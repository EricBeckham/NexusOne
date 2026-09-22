package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** Test double: copies aged hot events and purges expired archive rows without mutating the in-memory chain. */
public final class InMemoryAuditArchivePort implements AuditArchivePort {
    private final AuditPort hot;
    private final Map<UUID, ChainedAuditEvent> archived = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public InMemoryAuditArchivePort(AuditPort hot) {
        this.hot = Objects.requireNonNull(hot, "hot");
    }

    @Override
    public ArchiveRunResult copyHotOlderThan(Duration hotRetention, Instant now) {
        Objects.requireNonNull(hotRetention, "hotRetention");
        Objects.requireNonNull(now, "now");
        Instant cutoff = now.minus(hotRetention);
        lock.lock();
        try {
            int copied = 0;
            for (ChainedAuditEvent linked : hot.chain()) {
                if (!linked.event().occurredAt().isBefore(cutoff)) {
                    continue;
                }
                UUID id = linked.event().eventId();
                if (!archived.containsKey(id)) {
                    archived.put(id, linked);
                    copied++;
                }
            }
            return ArchiveRunResult.copied(copied, archived.size(), cutoff);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ArchiveRunResult purgeExpiredArchive(Duration archiveRetention, Instant now) {
        Objects.requireNonNull(archiveRetention, "archiveRetention");
        Objects.requireNonNull(now, "now");
        Instant cutoff = now.minus(archiveRetention);
        lock.lock();
        try {
            int before = archived.size();
            archived.values().removeIf(event -> event.event().occurredAt().isBefore(cutoff));
            return ArchiveRunResult.purged(before - archived.size(), archived.size(), cutoff);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChainedAuditEvent> findArchivedByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        lock.lock();
        try {
            return archived.values().stream()
                    .filter(event -> traceId.equals(event.event().traceId()))
                    .collect(Collectors.toUnmodifiableList());
        } finally {
            lock.unlock();
        }
    }

    public List<ChainedAuditEvent> archived() {
        lock.lock();
        try {
            return List.copyOf(new ArrayList<>(archived.values()));
        } finally {
            lock.unlock();
        }
    }
}
