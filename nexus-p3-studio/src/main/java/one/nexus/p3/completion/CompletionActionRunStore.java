package one.nexus.p3.completion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FA4: execution records for post-completion actions; every call is scoped by tenant. */
public interface CompletionActionRunStore {
    /**
     * Returns the run for (action, source record), creating it as PENDING when absent. A DONE run is returned as is so
     * the caller can skip it; otherwise {@code attempts} is incremented for this try.
     */
    CompletionActionRun claim(CompletionActionRun candidate);

    void markDone(UUID tenantId, UUID runId, UUID targetRecordId);

    void markFailed(UUID tenantId, UUID runId, String errorCode);

    Optional<CompletionActionRun> find(UUID tenantId, UUID runId);

    /** Newest first; {@code actionId} null lists every action of the tenant. */
    List<CompletionActionRun> list(UUID tenantId, UUID actionId, int limit);
}
