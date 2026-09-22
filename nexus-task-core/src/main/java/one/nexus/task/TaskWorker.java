package one.nexus.task;

import java.time.Clock;
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
import one.nexus.event.DeadLetterStore;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.JobLogWriter;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.task.jdbc.JdbcTaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public final class TaskWorker implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(TaskWorker.class);

    private final JdbcTaskQueue queue;
    private final ExecutionIsolation isolation;
    private final List<TaskHandler> handlers;
    private final IdempotencyStore idempotency;
    private final DeadLetterStore deadLetters;
    private final JobLogWriter jobLog;
    private final ExecutionSettings settings;
    private final boolean autoStartup;
    private final String workerId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public TaskWorker(
            JdbcTaskQueue queue,
            ExecutionIsolation isolation,
            List<TaskHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog,
            ExecutionSettings settings,
            Clock clock,
            boolean autoStartup
    ) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.handlers = new CopyOnWriteArrayList<>(handlers == null ? List.of() : handlers);
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
        this.jobLog = jobLog;
        this.settings = settings == null ? ExecutionSettings.defaults() : settings;
        this.autoStartup = autoStartup;
        this.workerId = "task-" + UUID.randomUUID();
    }

    public void register(TaskHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public int workOnce() {
        List<TaskRecord> batch = queue.leaseBatch(workerId, settings.batchSize());
        int done = 0;
        for (TaskRecord record : batch) {
            if (workOne(record)) {
                done++;
            }
        }
        return done;
    }

    public boolean workOne(TaskRecord record) {
        IsolatedEnvelope envelope = record.envelope();
        String key = record.id().toString();
        if (idempotency.exists("task-succeeded", key)) {
            queue.markSucceeded(record.id());
            log(record, "SUCCEEDED", "idempotent");
            return true;
        }
        try {
            isolation.runRestored(record.membership(), envelope, this::execute);
            idempotency.claim("task-succeeded", key, record.tenantId(), record.traceId());
            queue.markSucceeded(record.id());
            log(record, "SUCCEEDED", null);
            return true;
        } catch (RuntimeException ex) {
            boolean dead = queue.markRetryOrDead(record, truncate(ex.getMessage()));
            log(record, dead ? "DEAD" : "FAILED", truncate(ex.getMessage()));
            if (dead) {
                deadLetters.record(
                        "TASK",
                        record.id(),
                        record.tenantId(),
                        record.traceId(),
                        record.taskType(),
                        record.payload(),
                        truncate(ex.getMessage()),
                        record.attempt());
            }
            LOG.warn("task failed id={} type={} dead={} trace={}",
                    record.id(), record.taskType(), dead, record.traceId(), ex);
            return false;
        }
    }

    private void execute(IsolatedEnvelope envelope) {
        TaskHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(envelope.payloadType()))
                .findFirst()
                .orElseThrow(() -> new NexusException(
                        ErrorCode.JOB_UNKNOWN_TYPE, 500, "no task handler for " + envelope.payloadType()));
        handler.execute(envelope);
    }

    private void log(TaskRecord record, String status, String detail) {
        if (jobLog == null) {
            return;
        }
        jobLog.write("TASK", record.id(), record.taskType(), record.tenantId(), record.traceId(), status, detail);
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
            Thread thread = new Thread(r, "nexus-task-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                workOnce();
            } catch (RuntimeException ex) {
                LOG.warn("task poll failed", ex);
            }
        }, 0, settings.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
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
        return Integer.MAX_VALUE - 19;
    }
}
