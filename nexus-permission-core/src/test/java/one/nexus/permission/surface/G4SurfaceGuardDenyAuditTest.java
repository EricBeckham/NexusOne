package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
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
 * G4 leftover: SurfaceGuard / AuthorizedSubmit deny must land on the existing
 * hash chain via {@code appendNewTransaction}. Does not redesign same-TX
 * {@code append}. Does not claim a product G4 close.
 */
class G4SurfaceGuardDenyAuditTest {
    private static final String FORM_TRACE = "0199a1c0-0000-7000-8000-00000000c230";
    private static final String EXPORT_TRACE = "0199a1c0-0000-7000-8000-00000000c231";
    private static final String JOB_TRACE = "0199a1c0-0000-7000-8000-00000000c232";
    private static final String SILENT_TRACE = "0199a1c0-0000-7000-8000-00000000c233";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void surfaceGuardAndCatalogDenyEmitPermissionAndGuardReject() {
        Harness harness = Harness.open(Set.of());
        TraceIds.putMdc(FORM_TRACE);
        PermissionException form = assertThrows(
                PermissionException.class,
                () -> SurfaceGuard.require(harness.module, "form", "read"));
        assertEquals("P2.PERM.0003", form.errorCode().code());
        assertDeniedOnChain(harness.audits, FORM_TRACE, "form", "read");

        TraceIds.putMdc(EXPORT_TRACE);
        PermissionException export = assertThrows(
                PermissionException.class,
                () -> SurfaceCatalog.requireExport(harness.module.service(), harness.audits));
        assertEquals("P2.PERM.0003", export.errorCode().code());
        assertDeniedOnChain(harness.audits, EXPORT_TRACE, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);
        assertTrue(harness.audits.verifyChain().intact(), harness.audits.verifyChain().failure());
    }

    @Test
    void authorizedSubmitDenyEmitsAndSilentPathWritesNothing() {
        Harness harness = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        harness.module.matrix().installExample();
        TraceIds.putMdc(EXPORT_TRACE);
        assertThrows(PermissionException.class,
                () -> AuthorizedSubmit.export(harness.module.service(), harness.audits));
        assertDeniedOnChain(
                harness.audits, EXPORT_TRACE, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);

        TraceIds.putMdc(JOB_TRACE);
        assertThrows(PermissionException.class,
                () -> AuthorizedSubmit.job(harness.module.service(), harness.audits, "cleanup"));
        assertDeniedOnChain(harness.audits, JOB_TRACE, SurfaceCatalog.JOB_RESOURCE, SurfaceCatalog.JOB_ACTION);

        InMemoryHashChainedAuditPort silent =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        TraceIds.putMdc(SILENT_TRACE);
        assertThrows(PermissionException.class, () -> AuthorizedSubmit.task(harness.module.service(), "cleanup"));
        assertTrue(silent.findByTraceId(SILENT_TRACE).isEmpty(), silent.findByTraceId(SILENT_TRACE).toString());
        assertTrue(harness.audits.findByTraceId(SILENT_TRACE).isEmpty(),
                "silent AuthorizedSubmit overload must not write the module chain");
        assertTrue(harness.audits.verifyChain().intact());
    }

    @Test
    void isolationOnlySubmitSkipsEngineAndWritesNothing() {
        InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        TraceIds.putMdc(FORM_TRACE);
        assertEquals(null, AuthorizedSubmit.export(null, audits));
        assertTrue(audits.findByTraceId(FORM_TRACE).isEmpty());
    }

    private static void assertDeniedOnChain(
            InMemoryHashChainedAuditPort audits, String trace, String resource, String action) {
        List<ChainedAuditEvent> found = audits.findByTraceId(trace);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())),
                () -> "missing PERMISSION_DENIED for " + trace + ": " + found);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.GUARD_REJECT.equals(event.event().eventType())),
                () -> "missing GUARD_REJECT for " + trace + ": " + found);
        assertTrue(found.stream().anyMatch(event ->
                        resource.equals(event.event().details().get("resource"))
                                && action.equals(event.event().details().get("action"))),
                () -> "missing " + resource + "." + action + " details: " + found);
    }

    private record Harness(PermissionModule module, InMemoryHashChainedAuditPort audits) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("02300230-0230-4230-8230-023002300230");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g4-deny", Instant.now(), Instant.now().plusSeconds(60)));
            AtomicReference<Set<String>> roleRef = new AtomicReference<>(roles);
            InMemoryHashChainedAuditPort audits =
                    new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
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
            PermissionModule module = new PermissionModule(
                    repository, subjects, snapshots, new InMemoryPolicyInvalidation(), audits);
            return new Harness(module, audits);
        }
    }
}
