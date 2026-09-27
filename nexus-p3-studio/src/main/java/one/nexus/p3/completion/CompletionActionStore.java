package one.nexus.p3.completion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FA3: persistence for {@link CompletionAction}; every read and write is scoped by tenant. */
public interface CompletionActionStore {
    void insert(CompletionAction action);

    /** Optimistic update: false when {@code expectedVersion} no longer matches. */
    boolean update(CompletionAction action, long expectedVersion);

    Optional<CompletionAction> find(UUID tenantId, UUID id);

    List<CompletionAction> list(UUID tenantId);

    /** Enabled actions for a finished approval of {@code sourceLedgerCode} under {@code processKey}. */
    List<CompletionAction> enabledFor(UUID tenantId, String sourceLedgerCode, String processKey);
}
