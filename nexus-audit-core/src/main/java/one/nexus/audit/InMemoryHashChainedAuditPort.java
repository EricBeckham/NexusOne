package one.nexus.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoPort;

/** In-memory append-only store used as the M0-11 test double and default local adapter. */
public final class InMemoryHashChainedAuditPort implements AuditPort {
    private final AuditHashChain chain;
    private final List<ChainedAuditEvent> events = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private byte[] lastHash = AuditHashChain.GENESIS.clone();

    public InMemoryHashChainedAuditPort(CryptoPort crypto) {
        this.chain = new AuditHashChain(Objects.requireNonNull(crypto, "crypto"));
    }

    @Override
    public ChainedAuditEvent append(AuditEvent event) {
        return appendLinked(event);
    }

    @Override
    public ChainedAuditEvent appendNewTransaction(AuditEvent event) {
        return appendLinked(event);
    }

    private ChainedAuditEvent appendLinked(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        lock.lock();
        try {
            ChainedAuditEvent linked = chain.link(events.size(), lastHash, event);
            events.add(linked);
            lastHash = linked.eventHash();
            return linked;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChainedAuditEvent> findByTraceId(String traceId) {
        return filter(event -> event.event().traceId().equals(traceId));
    }

    @Override
    public List<ChainedAuditEvent> findByCorrelationId(String correlationId) {
        return filter(event -> correlationId.equals(event.event().correlationId()));
    }

    @Override
    public Optional<ChainedAuditEvent> findByEventId(UUID eventId) {
        return chain().stream().filter(event -> event.event().eventId().equals(eventId)).findFirst();
    }

    @Override
    public List<ChainedAuditEvent> chain() {
        lock.lock();
        try {
            return List.copyOf(events);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public HashChainVerification verifyChain() {
        return chain.verify(chain());
    }

    List<ChainedAuditEvent> copyWithTamperedHash(UUID eventId) {
        List<ChainedAuditEvent> copy = new ArrayList<>();
        for (ChainedAuditEvent linked : chain()) {
            if (linked.event().eventId().equals(eventId)) {
                byte[] broken = linked.eventHash();
                broken[0] ^= 1;
                copy.add(new ChainedAuditEvent(
                        linked.chainIndex(),
                        linked.event(),
                        linked.previousHash(),
                        broken,
                        linked.suite(),
                        linked.digestKeyVersion()));
            } else {
                copy.add(linked);
            }
        }
        return copy;
    }

    private List<ChainedAuditEvent> filter(java.util.function.Predicate<ChainedAuditEvent> predicate) {
        return chain().stream().filter(predicate).collect(Collectors.toUnmodifiableList());
    }
}
