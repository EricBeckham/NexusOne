package one.nexus.permission;

import java.time.Duration;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.permission.admin.PlatformDutyService;
import one.nexus.permission.admin.PolicyAdminService;
import one.nexus.permission.admin.RoleMatrixService;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.identity.IamSubjectDirectory;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.PolicyInvalidationPort;

/** Composition root so tests and Spring share the same wiring. */
public final class PermissionModule {
    private final PolicyRepository repository;
    private final SubjectDirectory subjects;
    private final SnapshotManager snapshots;
    private final PolicyAdminService admin;
    private final PermissionService service;
    private final RoleMatrixService matrix;
    private final PlatformDutyService duties;
    private final PolicyInvalidationPort invalidation;

    public PermissionModule(
            PolicyRepository repository,
            SubjectDirectory subjects,
            SnapshotManager snapshots,
            PolicyInvalidationPort invalidation
    ) {
        this(repository, subjects, snapshots, invalidation, null);
    }

    public PermissionModule(
            PolicyRepository repository,
            SubjectDirectory subjects,
            SnapshotManager snapshots,
            PolicyInvalidationPort invalidation,
            AuditPort audits
    ) {
        this.repository = repository;
        this.subjects = subjects;
        this.snapshots = snapshots;
        this.invalidation = invalidation;
        this.admin = new PolicyAdminService(repository, invalidation, audits);
        this.service = new PermissionService(repository, subjects, snapshots);
        this.matrix = new RoleMatrixService(repository, admin, service);
        this.duties = new PlatformDutyService(repository, admin, service);
    }

    public static PermissionModule create(
            PolicyRepository repository,
            IdentityRepository identity,
            PolicyInvalidationPort invalidation
    ) {
        return create(repository, identity, invalidation, null);
    }

    public static PermissionModule create(
            PolicyRepository repository,
            IdentityRepository identity,
            PolicyInvalidationPort invalidation,
            AuditPort audits
    ) {
        return create(repository, identity, invalidation, audits, SnapshotManager.DEFAULT_LOAD_TIMEOUT);
    }

    public static PermissionModule create(
            PolicyRepository repository,
            IdentityRepository identity,
            PolicyInvalidationPort invalidation,
            AuditPort audits,
            Duration snapshotLoadTimeout
    ) {
        SubjectDirectory directory = new IamSubjectDirectory(identity, repository);
        SnapshotManager manager = new SnapshotManager(
                tenant -> repository.currentVersion(UUID.fromString(tenant)),
                tenant -> repository.loadSnapshot(UUID.fromString(tenant)),
                snapshotLoadTimeout);
        if (invalidation != null) {
            invalidation.addListener(manager::notifyInvalidated);
        }
        return new PermissionModule(repository, directory, manager, invalidation, audits);
    }

    public PolicyRepository repository() {
        return repository;
    }

    public SubjectDirectory subjects() {
        return subjects;
    }

    public SnapshotManager snapshots() {
        return snapshots;
    }

    public PolicyAdminService admin() {
        return admin;
    }

    public PermissionService service() {
        return service;
    }

    public RoleMatrixService matrix() {
        return matrix;
    }

    public PlatformDutyService duties() {
        return duties;
    }

    public PolicyInvalidationPort invalidation() {
        return invalidation;
    }
}
