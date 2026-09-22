package one.nexus.p3.studio.form;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormStore {
    void insert(FormRecord form);

    void update(FormRecord form);

    Optional<FormRecord> find(UUID tenantId, UUID formId);

    Optional<FormRecord> findByCode(UUID tenantId, String formCode);

    List<FormRecord> list(UUID tenantId);

    void insertVersion(FormVersion version);

    void updateVersion(FormVersion version);

    List<FormVersion> versions(UUID tenantId, UUID formId);

    Optional<FormVersion> findVersion(UUID tenantId, UUID formId, int versionNo);

    Optional<FormVersion> findRunning(UUID tenantId, UUID formId);

    int nextVersionNo(UUID tenantId, UUID formId);

    void appendLog(PublishLog log);

    List<PublishLog> logs(UUID tenantId, UUID formId);

    void insertSubmission(FormSubmission submission);

    List<FormSubmission> submissions(UUID tenantId, UUID formId);

    default java.util.Optional<FormSubmission> findByLedgerRecord(UUID tenantId, UUID ledgerRecordId) {
        return java.util.Optional.empty();
    }

    /**
     * In-memory atomic section. JDBC stores rely on a Spring transaction around the same runnable.
     */
    default void runAtomically(Runnable action) {
        action.run();
    }
}
