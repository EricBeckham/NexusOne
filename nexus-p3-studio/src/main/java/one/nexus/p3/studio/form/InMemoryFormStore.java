package one.nexus.p3.studio.form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryFormStore implements FormStore {
    private final Map<UUID, FormRecord> forms = new ConcurrentHashMap<>();
    private final Map<UUID, List<FormVersion>> versions = new ConcurrentHashMap<>();
    private final Map<UUID, List<PublishLog>> logs = new ConcurrentHashMap<>();
    private final Map<UUID, List<FormSubmission>> submissions = new ConcurrentHashMap<>();

    @Override
    public synchronized void insert(FormRecord form) {
        forms.put(form.formId(), copy(form));
    }

    @Override
    public synchronized void update(FormRecord form) {
        forms.put(form.formId(), copy(form));
    }

    @Override
    public synchronized Optional<FormRecord> find(UUID tenantId, UUID formId) {
        FormRecord form = forms.get(formId);
        if (form == null || !form.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(copy(form));
    }

    @Override
    public synchronized Optional<FormRecord> findByCode(UUID tenantId, String formCode) {
        return forms.values().stream()
                .filter(form -> form.tenantId().equals(tenantId) && form.formCode().equals(formCode))
                .map(InMemoryFormStore::copy)
                .findFirst();
    }

    @Override
    public synchronized List<FormRecord> list(UUID tenantId) {
        return forms.values().stream()
                .filter(form -> form.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(FormRecord::updatedAt).reversed())
                .map(InMemoryFormStore::copy)
                .toList();
    }

    @Override
    public synchronized void insertVersion(FormVersion version) {
        versions.computeIfAbsent(version.formId(), key -> new CopyOnWriteArrayList<>()).add(copy(version));
    }

    @Override
    public synchronized void updateVersion(FormVersion version) {
        List<FormVersion> list = versions.computeIfAbsent(version.formId(), key -> new CopyOnWriteArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).versionId().equals(version.versionId())) {
                list.set(i, copy(version));
                return;
            }
        }
        list.add(copy(version));
    }

    @Override
    public synchronized List<FormVersion> versions(UUID tenantId, UUID formId) {
        return versions.getOrDefault(formId, List.of()).stream()
                .filter(version -> version.tenantId().equals(tenantId))
                .sorted(Comparator.comparingInt(FormVersion::versionNo))
                .map(InMemoryFormStore::copy)
                .toList();
    }

    @Override
    public synchronized Optional<FormVersion> findVersion(UUID tenantId, UUID formId, int versionNo) {
        return versions.getOrDefault(formId, List.of()).stream()
                .filter(version -> version.tenantId().equals(tenantId) && version.versionNo() == versionNo)
                .map(InMemoryFormStore::copy)
                .findFirst();
    }

    @Override
    public synchronized Optional<FormVersion> findRunning(UUID tenantId, UUID formId) {
        return versions.getOrDefault(formId, List.of()).stream()
                .filter(version -> version.tenantId().equals(tenantId) && version.status() == VersionStatus.RUNNING)
                .map(InMemoryFormStore::copy)
                .findFirst();
    }

    @Override
    public synchronized int nextVersionNo(UUID tenantId, UUID formId) {
        return versions.getOrDefault(formId, List.of()).stream()
                .filter(version -> version.tenantId().equals(tenantId))
                .mapToInt(FormVersion::versionNo)
                .max()
                .orElse(0)
                + 1;
    }

    @Override
    public synchronized void appendLog(PublishLog log) {
        logs.computeIfAbsent(log.formId(), key -> new CopyOnWriteArrayList<>()).add(log);
    }

    @Override
    public synchronized List<PublishLog> logs(UUID tenantId, UUID formId) {
        return logs.getOrDefault(formId, List.of()).stream()
                .filter(log -> log.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(PublishLog::occurredAt))
                .toList();
    }

    @Override
    public synchronized void insertSubmission(FormSubmission submission) {
        submissions.computeIfAbsent(submission.formId(), key -> new CopyOnWriteArrayList<>()).add(submission);
    }

    @Override
    public synchronized List<FormSubmission> submissions(UUID tenantId, UUID formId) {
        return submissions.getOrDefault(formId, List.of()).stream()
                .filter(row -> row.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(FormSubmission::submittedAt).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<FormSubmission> findByLedgerRecord(UUID tenantId, UUID ledgerRecordId) {
        return submissions.values().stream()
                .flatMap(List::stream)
                .filter(row -> row.tenantId().equals(tenantId) && ledgerRecordId.equals(row.ledgerRecordId()))
                .findFirst();
    }

    @Override
    public synchronized void runAtomically(Runnable action) {
        Map<UUID, FormRecord> formCopy = copyForms();
        Map<UUID, List<FormVersion>> versionCopy = copyVersions();
        Map<UUID, List<PublishLog>> logCopy = copyLogs();
        Map<UUID, List<FormSubmission>> submissionCopy = copySubmissions();
        try {
            action.run();
        } catch (RuntimeException ex) {
            forms.clear();
            forms.putAll(formCopy);
            versions.clear();
            versions.putAll(versionCopy);
            logs.clear();
            logs.putAll(logCopy);
            submissions.clear();
            submissions.putAll(submissionCopy);
            throw ex;
        }
    }

    private Map<UUID, FormRecord> copyForms() {
        Map<UUID, FormRecord> copy = new ConcurrentHashMap<>();
        forms.forEach((id, form) -> copy.put(id, copy(form)));
        return copy;
    }

    private Map<UUID, List<FormVersion>> copyVersions() {
        Map<UUID, List<FormVersion>> copy = new ConcurrentHashMap<>();
        versions.forEach((id, list) -> {
            List<FormVersion> cloned = new CopyOnWriteArrayList<>();
            list.forEach(version -> cloned.add(copy(version)));
            copy.put(id, cloned);
        });
        return copy;
    }

    private Map<UUID, List<PublishLog>> copyLogs() {
        Map<UUID, List<PublishLog>> copy = new ConcurrentHashMap<>();
        logs.forEach((id, list) -> copy.put(id, new CopyOnWriteArrayList<>(list)));
        return copy;
    }

    private Map<UUID, List<FormSubmission>> copySubmissions() {
        Map<UUID, List<FormSubmission>> copy = new ConcurrentHashMap<>();
        submissions.forEach((id, list) -> copy.put(id, new CopyOnWriteArrayList<>(list)));
        return copy;
    }

    private static FormRecord copy(FormRecord form) {
        return new FormRecord(
                form.formId(),
                form.tenantId(),
                form.appCode(),
                form.formCode(),
                form.name(),
                form.description(),
                form.draftStatus(),
                form.runningVersion(),
                new ArrayList<>(form.draftBindings()),
                form.createdAt(),
                form.createdBy(),
                form.updatedAt(),
                form.updatedBy());
    }

    private static FormVersion copy(FormVersion version) {
        return new FormVersion(
                version.versionId(),
                version.formId(),
                version.tenantId(),
                version.versionNo(),
                version.status(),
                version.snapshot(),
                version.notes(),
                version.createdAt(),
                version.createdBy());
    }
}
