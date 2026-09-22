package one.nexus.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.FileObjectKeyGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * G10 file half: match {@code files.file_object} rows to object keys under a tenant prefix.
 * Does not reconstruct bytes (not G6 restore). Repair stays inside the bound prefix.
 */
public final class FileObjectReconciler {
    public static final String PLATFORM_PATH = "files.reconcile";

    public enum Kind {
        MATCHED,
        MISSING,
        ORPHAN,
        LEFTOVER,
        DRIFT,
        CONFLICT
    }

    public enum Action {
        NONE,
        MARK_MISSING,
        DELETE_ORPHAN,
        DELETE_LEFTOVER,
        MARK_UPLOADED
    }

    public record Finding(
            Kind kind,
            UUID fileId,
            UUID tenantId,
            String objectKey,
            String detail
    ) {}

    public record Repair(
            Kind kind,
            Action action,
            UUID fileId,
            UUID tenantId,
            String objectKey,
            String detail
    ) {}

    public record Report(
            boolean applied,
            String path,
            int matched,
            int missing,
            int orphan,
            int leftover,
            int drifted,
            int conflicts,
            int repaired,
            List<Finding> findings,
            List<Repair> repairs
    ) {}

    private final ObjectStoragePort storage;
    private final FileObjectRepository repository;
    private final String bucket;

    public FileObjectReconciler(
            ObjectStoragePort storage,
            FileObjectRepository repository,
            String bucket) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bucket = FileService.requireSegment(bucket, "bucket");
    }

    public Report reconcile(boolean apply) {
        AccessContext context = AccessContextHolder.current();
        if (context instanceof TenantContext tenant) {
            return reconcile(apply, UUID.fromString(tenant.tenantId()));
        }
        if (context instanceof PlatformContext) {
            return reconcilePlatform(apply);
        }
        throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "tenant or platform context required");
    }

    public Report reconcile(boolean apply, UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        AccessContext context = AccessContextHolder.current();
        if (context instanceof TenantContext tenant && !tenant.tenantId().equals(tenantId.toString())) {
            throw new NexusException(ErrorCode.FILE_DENIED, 403, "reconcile tenant mismatch");
        }
        if (!(context instanceof TenantContext) && !(context instanceof PlatformContext)) {
            throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "tenant or platform context required");
        }
        String prefix = tenantPrefix(tenantId);
        FileObjectKeyGuard.assertOwned(context, prefix);
        List<String> keys = storage.listKeys(bucket, prefix);
        return compare(apply, context, repository.listByTenant(tenantId), keys, Set.of(tenantId));
    }

    private Report reconcilePlatform(boolean apply) {
        AccessContext context = AccessContextHolder.current();
        FileObjectKeyGuard.assertOwned(context, "tenants/");
        List<String> keys = new ArrayList<>(storage.listKeys(bucket, "tenants/"));
        FileObjectKeyGuard.assertOwned(context, "platform/");
        keys.addAll(storage.listKeys(bucket, "platform/"));
        List<FileObject> rows = repository.listAll();
        Set<UUID> knownTenants = new HashSet<>();
        for (FileObject file : rows) {
            knownTenants.add(file.tenantId());
        }
        return compare(apply, context, rows, keys, knownTenants);
    }

    private Report compare(
            boolean apply,
            AccessContext context,
            List<FileObject> rows,
            List<String> listedKeys,
            Set<UUID> repairableTenants
    ) {
        Set<String> present = new HashSet<>();
        for (String key : listedKeys) {
            if (key != null && !key.isBlank() && !key.endsWith("/")) {
                present.add(key);
            }
        }
        Set<String> accounted = new HashSet<>();
        List<Finding> findings = new ArrayList<>();
        List<Repair> repairs = new ArrayList<>();

        for (FileObject file : rows) {
            accounted.add(file.objectKey());
            if (!ownedByTenant(file.tenantId(), file.objectKey())
                    && !file.objectKey().startsWith("platform/")) {
                findings.add(new Finding(
                        Kind.CONFLICT, file.fileId(), file.tenantId(), file.objectKey(),
                        "metadata object_key is outside the file tenant prefix"));
                continue;
            }
            try {
                FileObjectKeyGuard.assertOwned(context, file.objectKey());
            } catch (RuntimeException ex) {
                findings.add(new Finding(
                        Kind.CONFLICT, file.fileId(), file.tenantId(), file.objectKey(),
                        "object_key is not owned by the bound context"));
                continue;
            }
            boolean exists = present.contains(file.objectKey());
            if (file.status() == FileStatus.UPLOADED && exists) {
                findings.add(new Finding(
                        Kind.MATCHED, file.fileId(), file.tenantId(), file.objectKey(),
                        "metadata matches object"));
                continue;
            }
            if (file.status() == FileStatus.UPLOADED) {
                findings.add(new Finding(
                        Kind.MISSING, file.fileId(), file.tenantId(), file.objectKey(),
                        "uploaded metadata has no object under the tenant prefix"));
                if (apply) {
                    repository.update(file.missing());
                    repairs.add(new Repair(
                            Kind.MISSING, Action.MARK_MISSING, file.fileId(), file.tenantId(),
                            file.objectKey(), "marked metadata MISSING; bytes were not reconstructed"));
                }
                continue;
            }
            if (file.status() == FileStatus.MISSING && !exists) {
                findings.add(new Finding(
                        Kind.MATCHED, file.fileId(), file.tenantId(), file.objectKey(),
                        "missing metadata still has no object"));
                continue;
            }
            if (file.status() == FileStatus.MISSING) {
                findings.add(new Finding(
                        Kind.DRIFT, file.fileId(), file.tenantId(), file.objectKey(),
                        "object is present again; metadata still MISSING"));
                if (apply) {
                    repository.update(file.uploaded());
                    repairs.add(new Repair(
                            Kind.DRIFT, Action.MARK_UPLOADED, file.fileId(), file.tenantId(),
                            file.objectKey(), "marked metadata UPLOADED after object reappeared"));
                }
                continue;
            }
            if (file.status() == FileStatus.DELETED && !exists) {
                findings.add(new Finding(
                        Kind.MATCHED, file.fileId(), file.tenantId(), file.objectKey(),
                        "deleted metadata and object are both gone"));
                continue;
            }
            if (file.status() == FileStatus.DELETED) {
                findings.add(new Finding(
                        Kind.LEFTOVER, file.fileId(), file.tenantId(), file.objectKey(),
                        "deleted metadata still has an object under the tenant prefix"));
                if (apply) {
                    deleteOwned(context, file.objectKey());
                    repairs.add(new Repair(
                            Kind.LEFTOVER, Action.DELETE_LEFTOVER, file.fileId(), file.tenantId(),
                            file.objectKey(), "removed leftover object for deleted metadata"));
                }
            }
        }

        for (String key : present) {
            if (accounted.contains(key)) {
                continue;
            }
            Optional<UUID> keyTenant = tenantIdFromKey(key);
            UUID tenantId = keyTenant.orElse(null);
            if (context instanceof TenantContext tenant && !key.startsWith(tenantPrefix(UUID.fromString(tenant.tenantId())))) {
                findings.add(new Finding(
                        Kind.CONFLICT, null, tenantId, key,
                        "listed key is outside the bound tenant prefix"));
                continue;
            }
            try {
                FileObjectKeyGuard.assertOwned(context, key);
            } catch (RuntimeException ex) {
                findings.add(new Finding(
                        Kind.CONFLICT, null, tenantId, key,
                        "orphan key is not owned by the bound context"));
                continue;
            }
            boolean repairable = key.startsWith("platform/")
                    || (keyTenant.isPresent() && repairableTenants.contains(keyTenant.get()));
            findings.add(new Finding(
                    Kind.ORPHAN, null, tenantId, key,
                    repairable
                            ? "object has no file metadata under the tenant prefix"
                            : "object has no file metadata; unknown tenant prefix is report-only"));
            if (apply && repairable) {
                deleteOwned(context, key);
                repairs.add(new Repair(
                        Kind.ORPHAN, Action.DELETE_ORPHAN, null, tenantId, key,
                        "removed orphan object under the owned tenant prefix"));
            }
        }

        return summarize(apply, findings, repairs);
    }

    private void deleteOwned(AccessContext context, String objectKey) {
        FileObjectKeyGuard.assertOwned(context, objectKey);
        storage.delete(bucket, objectKey);
    }

    static String tenantPrefix(UUID tenantId) {
        return "tenants/" + tenantId + "/";
    }

    static boolean ownedByTenant(UUID tenantId, String objectKey) {
        return objectKey != null && objectKey.startsWith(tenantPrefix(tenantId));
    }

    static Optional<UUID> tenantIdFromKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith("tenants/")) {
            return Optional.empty();
        }
        int slash = objectKey.indexOf('/', "tenants/".length());
        if (slash <= "tenants/".length()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(objectKey.substring("tenants/".length(), slash)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static boolean applyFlag(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        return payload.replaceAll("\\s+", "").contains("\"apply\":true");
    }

    private static Report summarize(boolean apply, List<Finding> findings, List<Repair> repairs) {
        int matched = 0;
        int missing = 0;
        int orphan = 0;
        int leftover = 0;
        int drifted = 0;
        int conflicts = 0;
        for (Finding finding : findings) {
            switch (finding.kind()) {
                case MATCHED -> matched++;
                case MISSING -> missing++;
                case ORPHAN -> orphan++;
                case LEFTOVER -> leftover++;
                case DRIFT -> drifted++;
                case CONFLICT -> conflicts++;
            }
        }
        return new Report(
                apply, PLATFORM_PATH, matched, missing, orphan, leftover, drifted, conflicts,
                repairs.size(), List.copyOf(findings), List.copyOf(repairs));
    }
}
