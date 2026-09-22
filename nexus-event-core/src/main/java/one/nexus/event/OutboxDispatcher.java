package one.nexus.event;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Independent dispatcher: SKIP LOCKED lease poll, at-least-once delivery, restore
 * tenant via {@link one.nexus.isolation.context.RequestIsolationPolicy}.
 */
public final class OutboxDispatcher implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final JdbcOutboxStore store;
    private final ExecutionIsolation isolation;
    private final List<OutboxHandler> handlers;
    private final IdempotencyStore idempotency;
    private final DeadLetterStore deadLetters;
    private final JobLogWriter jobLog;
    private final ExecutionSettings settings;
    private final DeliveryLagProbe lagProbe;
    private final Clock clock;
    private final String workerId;
    private final boolean autoStartup;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public OutboxDispatcher(
            JdbcOutboxStore store,
            ExecutionIsolation isolation,
            List<OutboxHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog,
            ExecutionSettings settings,
            Clock clock
    ) {
        this(store, isolation, handlers, idempotency, deadLetters, jobLog, settings, clock, false);
    }

    public OutboxDispatcher(
            JdbcOutboxStore store,
            ExecutionIsolation isolation,
            List<OutboxHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog,
            ExecutionSettings settings,
            Clock clock,
            boolean autoStartup
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handlers = new CopyOnWriteArrayList<>(handlers == null ? List.of() : handlers);
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
        this.jobLog = jobLog;
        this.settings = settings == null ? ExecutionSettings.defaults() : settings;
        this.lagProbe = new DeliveryLagProbe(this.settings.lagWarnAfter());
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.workerId = "outbox-" + UUID.randomUUID();
        this.autoStartup = autoStartup;
    }

    public void register(OutboxHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public int dispatchOnce() {
        lagProbe.warnIfLagging(store.oldestPendingCreatedAt(), Instant.now(clock));
        List<OutboxRecord> batch = store.leaseBatch(workerId, settings.batchSize());
        int delivered = 0;
        for (OutboxRecord record : batch) {
            if (dispatchOne(record)) {
                delivered++;
            }
        }
        return delivered;
    }

    public boolean dispatchOne(OutboxRecord record) {
        IsolatedEnvelope envelope = record.envelope();
        String dispatchKey = record.id().toString();
        if (idempotency.exists("outbox-dispatched", dispatchKey)) {
            store.markDispatched(record.id());
            log(record, "DISPATCHED", "idempotent");
            return true;
        }
        try {
            isolation.runRestored(record.membership(), envelope, this::deliver);
            idempotency.claim("outbox-dispatched", dispatchKey, record.tenantId(), record.traceId());
            store.markDispatched(record.id());
            log(record, "DISPATCHED", null);
            return true;
        } catch (RuntimeException ex) {
            boolean dead = store.markRetryOrDead(record, truncate(ex.getMessage()));
            log(record, dead ? "DEAD" : "FAILED", truncate(ex.getMessage()));
            if (dead) {
                deadLetters.record(
                        "OUTBOX",
                        record.id(),
                        record.tenantId(),
                        record.traceId(),
                        record.destination(),
                        record.payload(),
                        truncate(ex.getMessage()),
                        record.attempt());
            }
            LOG.warn("outbox dispatch failed id={} dest={} dead={} trace={}",
                    record.id(), record.destination(), dead, record.traceId(), ex);
            return false;
        }
    }

    private void deliver(IsolatedEnvelope envelope) {
        OutboxHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(envelope.payloadType()))
                .findFirst()
                .orElseThrow(() -> new NexusException(
                        ErrorCode.EVT_UNKNOWN_DESTINATION,
                        500,
                        "no outbox handler for " + envelope.payloadType()));
        handler.onDispatch(envelope);
    }

    private void log(OutboxRecord record, String status, String detail) {
        if (jobLog == null) {
            return;
        }
        jobLog.write("OUTBOX", record.id(), record.destination(), record.tenantId(), record.traceId(), status, detail);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "nexus-outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        long millis = settings.pollInterval().toMillis();
        executor.scheduleWithFixedDelay(() -> {
            try {
                dispatchOnce();
            } catch (RuntimeException ex) {
                LOG.warn("outbox poll failed", ex);
            }
        }, 0, millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 20;
    }
}
