package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G4 leftover: export wizard begin / complete land on the existing hash chain
 * via {@code AuditPort.append}. Deny reuses SurfaceGuard
 * {@code appendNewTransaction}. Same-TX rules are not redesigned.
 */
class G4ExportWizardAuditTest {
    private static final String BEGIN_TRACE = "0199a1c0-0000-7000-8000-00000000c250";
    private static final String DENY_TRACE = "0199a1c0-0000-7000-8000-00000000c251";
    private static final String WIZARD_ID = "0199a1c0-0000-7000-8000-00000000c252";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void beginAndCompleteLandExportEventsOnSameChain() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER), true);
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of(), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person(harness.tenant.toString()),
                        harness.tenant.toString(),
                        "mem-wizard",
                        Set.of(harness.tenant.toString())),
                BEGIN_TRACE));
        TraceIds.putMdc(BEGIN_TRACE);
        AuthorizedExport.beginWizard(harness.module.service(), harness.audits, WIZARD_ID);
        AuthorizedExport.completeWizard(
                harness.module.service(),
                harness.audits,
                harness.tenant.toString(),
                "{\"surface\":\"export.wizard\"}",
                WIZARD_ID);

        var found = harness.audits.findByTraceId(BEGIN_TRACE);
        assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())
                        && event.event().category() == AuditCategory.EXPORT),
                () -> "missing begin: " + found);
        assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType())
                        && event.event().category() == AuditCategory.EXPORT),
                () -> "missing complete: " + found);
        assertTrue(found.stream().allMatch(event -> BEGIN_TRACE.equals(event.event().traceId())));
        assertTrue(harness.audits.verifyChain().intact(), harness.audits.verifyChain().failure());
    }

    @Test
    void denyBeginWritesPermissionAndGuardRejectAndSkipsIsolatedExport() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER), true);
        TraceIds.putMdc(DENY_TRACE);
        PermissionException denied = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.beginWizard(harness.module.service(), harness.audits, WIZARD_ID));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        var found = harness.audits.findByTraceId(DENY_TRACE);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing deny: " + found);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing guard-reject: " + found);
        assertTrue(found.stream().noneMatch(event -> AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())));
        assertTrue(harness.audits.verifyChain().intact(), harness.audits.verifyChain().failure());
    }

    @Test
    void isolationOnlySkipsWizardAuditWrite() {
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        TraceIds.putMdc(BEGIN_TRACE);
        AuthorizedExport.beginWizard(null, audits, WIZARD_ID);
        assertTrue(audits.findByTraceId(BEGIN_TRACE).isEmpty(), "no P2 context must not write begin");
        assertEquals(IsolatedExport.TASK_TYPE, SurfaceCatalog.EXPORT_RESOURCE);
    }

    private record Harness(PermissionModule module, InMemoryHashChainedAuditPort audits, UUID tenant) {
        static Harness open(Set<String> roles, boolean installExample) {
            UUID id = UUID.fromString("55555555-5555-4555-8555-555555555555");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g4-wizard", Instant.now(), Instant.now().plusSeconds(60)));
            AtomicReference<Set<String>> roleRef = new AtomicReference<>(roles);
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(TenantContext context) {
                    return new Subject(
                            context.tenantId().toString(),
                            context.personId().toString(),
                            roleRef.get(),
                            Set.of(),
                            Set.of("d1"));
                }

                @Override
                public DepartmentCatalog catalog(UUID tenantId) {
                    return DepartmentCatalog.EMPTY;
                }
            };
            SnapshotManager snapshots = new SnapshotManager(
                    tenant -> repository.currentVersion(UUID.fromString(tenant)),
                    tenant -> repository.loadSnapshot(UUID.fromString(tenant)));
            InMemoryHashChainedAuditPort audits =
                    new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
            PermissionModule module = new PermissionModule(
                    repository, subjects, snapshots, new InMemoryPolicyInvalidation(), audits);
            if (installExample) {
                module.matrix().installExample();
            }
            return new Harness(module, audits, id);
        }
    }
}
