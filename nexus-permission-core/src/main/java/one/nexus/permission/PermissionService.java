package one.nexus.permission;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.SnapshotUnavailableException;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.ScopedQuery;
import one.nexus.permission.support.PermissionException;

/** Unified decision entry used by UI / API / list / detail / flow / form studio / form fill / export / Job / Task. */
public final class PermissionService {
    private final PolicyRepository repository;
    private final SubjectDirectory subjects;
    private final SnapshotManager snapshots;

    public PermissionService(PolicyRepository repository, SubjectDirectory subjects, SnapshotManager snapshots) {
        this.repository = repository;
        this.subjects = subjects;
        this.snapshots = snapshots;
    }

    public Decision check(String resource, String action) {
        Decision decision = decide(resource, action, Predicate.TRUE, false);
        if (!decision.allowed()) {
            throw PermissionException.denied(decision.reason());
        }
        return decision;
    }

    public Decision decide(String resource, String action) {
        return decide(resource, action, Predicate.TRUE, false);
    }

    public Decision decide(String resource, String action, Predicate context, boolean highRiskWrite) {
        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            throw PermissionException.missingContext();
        }
        Subject subject = subjects.resolve(tenant);
        PolicySnapshot snapshot;
        try {
            snapshot = snapshots.get(tenant.tenantId().toString(), highRiskWrite);
        } catch (SnapshotUnavailableException ex) {
            throw PermissionException.snapshotUnavailable(ex.getMessage());
        }
        Set<String> keys = principalKeys(subject);
        List<Predicate> shares = repository.recordShares(tenant.tenantId(), resource, keys);
        DepartmentCatalog catalog = subjects.catalog(tenant.tenantId());
        return PermissionEngine.evaluate(
                subject, tenant.tenantId().toString(), resource, action, snapshot, shares, context, catalog);
    }

    public ScopedQuery scopedQuery(String resource, String action) {
        Decision decision = check(resource, action);
        return PermissionQueryBinder.bind(TenantContextHolder.require().tenantId().toString(), decision);
    }

    public Subject currentSubject() {
        return subjects.resolve(TenantContextHolder.require());
    }

    public boolean holdsPlatformDuty() {
        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            return false;
        }
        try {
            Subject subject = subjects.resolve(tenant);
            PolicySnapshot snapshot = snapshots.get(tenant.tenantId().toString());
            return !PlatformDutyCatalog.heldDuties(PermissionEngine.expandedRoleCodes(subject, snapshot)).isEmpty();
        } catch (SnapshotUnavailableException | PermissionException ex) {
            return false;
        }
    }

    public PolicySnapshot snapshot() {
        UUID tenantId = TenantContextHolder.require().tenantId();
        try {
            return snapshots.get(tenantId.toString());
        } catch (SnapshotUnavailableException ex) {
            throw PermissionException.snapshotUnavailable(ex.getMessage());
        }
    }

    public static Set<String> principalKeys(Subject subject) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("user:" + subject.userId());
        subject.roles().forEach(role -> keys.add("role:" + role));
        subject.positions().forEach(position -> keys.add("position:" + position));
        subject.departments().forEach(department -> keys.add("department:" + department));
        return keys;
    }
}
