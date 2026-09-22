package one.nexus.p3.studio.form;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FormRecord(
        UUID formId,
        UUID tenantId,
        String appCode,
        String formCode,
        String name,
        String description,
        FormStatus draftStatus,
        Integer runningVersion,
        List<FormBinding> draftBindings,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public FormRecord withDraft(FormStatus status, List<FormBinding> bindings, Instant at, String actor) {
        return new FormRecord(
                formId, tenantId, appCode, formCode, name, description,
                status, runningVersion, List.copyOf(bindings), createdAt, createdBy, at, actor);
    }

    public FormRecord withMeta(String name, String description, Instant at, String actor) {
        return new FormRecord(
                formId, tenantId, appCode, formCode, name, description,
                FormStatus.DRAFT, runningVersion, draftBindings, createdAt, createdBy, at, actor);
    }

    public FormRecord withRunning(int version, Instant at, String actor) {
        return new FormRecord(
                formId, tenantId, appCode, formCode, name, description,
                FormStatus.DRAFT, version, draftBindings, createdAt, createdBy, at, actor);
    }
}
