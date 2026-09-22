package one.nexus.task.quartz;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.event.DeadLetterStore;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.JobLogWriter;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.task.TaskHandler;

/** Process-wide Quartz job runtime. IsolatedQuartzJob is constructed by Quartz. */
public final class ExecutionRuntime {
    private static final AtomicReference<ExecutionRuntime> CURRENT = new AtomicReference<>();

    private final ExecutionIsolation isolation;
    private final List<TaskHandler> jobHandlers;
    private final IdempotencyStore idempotency;
    private final DeadLetterStore deadLetters;
    private final JobLogWriter jobLog;

    public ExecutionRuntime(
            ExecutionIsolation isolation,
            List<TaskHandler> jobHandlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog
    ) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.jobHandlers = new CopyOnWriteArrayList<>(jobHandlers == null ? List.of() : jobHandlers);
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.deadLetters = deadLetters;
        this.jobLog = jobLog;
    }

    public static void install(ExecutionRuntime runtime) {
        CURRENT.set(Objects.requireNonNull(runtime, "runtime"));
    }

    public static ExecutionRuntime get() {
        ExecutionRuntime runtime = CURRENT.get();
        if (runtime == null) {
            throw new IllegalStateException("ExecutionRuntime is not installed");
        }
        return runtime;
    }

    public static void clear() {
        CURRENT.set(null);
    }

    public ExecutionIsolation isolation() {
        return isolation;
    }

    public List<TaskHandler> jobHandlers() {
        return jobHandlers;
    }

    public void register(TaskHandler handler) {
        jobHandlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public IdempotencyStore idempotency() {
        return idempotency;
    }

    public DeadLetterStore deadLetters() {
        return deadLetters;
    }

    public JobLogWriter jobLog() {
        return jobLog;
    }
}
