package one.nexus.p3.studio.form;

import java.time.Instant;
import java.util.UUID;

public record FormVersion(
        UUID versionId,
        UUID formId,
        UUID tenantId,
        int versionNo,
        VersionStatus status,
        FormSnapshot snapshot,
        String notes,
        Instant createdAt,
        String createdBy) {

    public FormVersion withStatus(VersionStatus next) {
        return new FormVersion(
                versionId, formId, tenantId, versionNo, next, snapshot, notes, createdAt, createdBy);
    }
}
