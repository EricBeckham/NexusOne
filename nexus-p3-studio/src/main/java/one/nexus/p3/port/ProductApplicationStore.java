package one.nexus.p3.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for K15 product applications: the tenant's install-time
 * pin, the pinned platform template snapshot, and
 * {@code business_meta.product_application} rows. Keeps JDBC out of
 * {@code one.nexus.p3.application} (G1).
 */
public interface ProductApplicationStore {

    /** Pin of an {@code INSTALLED} installation owned by {@code tenantId}; empty when absent or foreign. */
    Optional<InstalledPin> findInstalledPin(UUID tenantId, UUID installationId);

    /** {@code fields_snapshot} JSON text of a platform form template version; empty when missing. */
    Optional<String> formTemplateSnapshot(UUID formTemplateVersionId);

    List<ApplicationRow> listByInitiator(UUID tenantId, UUID initiatorPersonId);

    void insertRunning(NewApplication application);

    void attachProcessInstance(UUID tenantId, UUID applicationId, UUID processInstanceId, Instant updatedAt);

    /** Flowable task id of the most recent acted todo on the instance; empty when none acted yet. */
    Optional<String> latestActedTaskId(UUID tenantId, UUID processInstanceId);

    record InstalledPin(UUID formTemplateVersionId, UUID productDefinitionId,
            String processDefinitionKey, Integer processDefinitionVersion) { }

    record ApplicationRow(UUID id, UUID installationId, String status, UUID processInstanceId, Instant createdAt) { }

    record NewApplication(UUID id, UUID tenantId, UUID installationId, UUID productDefinitionId,
            UUID formTemplateVersionId, String valuesJson, UUID initiatorPersonId, Instant createdAt) { }
}
