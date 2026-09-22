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
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.boundary.IsolatedExport;
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
 * G4 leftover after #152: export-wizard <strong>complete deny</strong> after a
 * successful begin lands the #136 pair and skips IsolatedExport. Same-TX
 * {@code append} / {@code appendNewTransaction} are not redesigned. Does not
 * claim a product G4 close.
 */
class G4ExportWizardDenyAuditTest {
    private static final String BEGIN_TRACE = "0199a1c0-0000-7000-8000-00000000c260";
    private static final String COMPLETE_DENY_TRACE = "0199a1c0-0000-7000-8000-00000000c261";
    private static final String WIZARD_ID = "0199a1c0-0000-7000-8000-00000000c262";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void completeAfterBeginDenyWritesPairAndSkipsIsolatedExport() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.DEPT_MANAGER), true);
        TraceIds.putMdc(BEGIN_TRACE);
        AuthorizedExport.beginWizard(harness.module.service(), harness.audits, WIZARD_ID);
        assertTrue(harness.audits.findByTraceId(BEGIN_TRACE).stream()
                .anyMatch(event -> AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())
                        && event.event().category() == AuditCategory.EXPORT));

        harness.roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        TraceIds.putMdc(COMPLETE_DENY_TRACE);
        PermissionException denied = assertThrows(
                PermissionException.class,
                () -> AuthorizedExport.completeWizard(
                        harness.module.service(),
                        harness.audits,
                        harness.tenant.toString(),
                        "{\"surface\":\"export.wizard\"}",
                        WIZARD_ID));
        assertEquals("P2.PERM.0003", denied.errorCode().code());

        var found = harness.audits.findByTraceId(COMPLETE_DENY_TRACE);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing deny: " + found);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing guard-reject: " + found);
        assertTrue(found.stream().noneMatch(event -> AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType())));
        assertTrue(found.stream().noneMatch(event -> AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType())));
        assertTrue(found.stream().noneMatch(event -> AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())));
        assertEquals(IsolatedExport.TASK_TYPE, SurfaceCatalog.EXPORT_RESOURCE);
        assertTrue(harness.audits.verifyChain().intact(), harness.audits.verifyChain().failure());
    }

    private record Harness(
            PermissionModule module,
            InMemoryHashChainedAuditPort audits,
            UUID tenant,
            AtomicReference<Set<String>> roles) {
        static Harness open(Set<String> roles, boolean installExample) {
            UUID id = UUID.fromString("55555555-5555-4555-8555-555555555556");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g4-wizard-deny", Instant.now(), Instant.now().plusSeconds(60)));
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
            return new Harness(module, audits, id, roleRef);
        }
    }
}
