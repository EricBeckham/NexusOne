package one.nexus.p3.infra.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * G2 leftover honesty: FormSurfaceGuard {@code form.write} + submit-start
 * dual-check stay always-green; live JAR + issued-context HTTP is honest
 * SKIP when unavailable. Does not redo #112 / #116 / #137 / #144 / #150 /
 * #155 / #163. Does not claim product G2 close.
 */
class G2FormWriteHonestyTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void requireFillIsFormWriteOnTheSameEngine() {
        assertTrue(G2FormWriteHonesty.requireFillIsFormWrite());
        assertEquals("form", G2FormWriteHonesty.FILL_RESOURCE);
        assertEquals("write", G2FormWriteHonesty.FILL_ACTION);
        assertEquals(FormSurfaceGuard.RESOURCE, G2FormWriteHonesty.FILL_RESOURCE);
        assertEquals(FormSurfaceGuard.WRITE, G2FormWriteHonesty.FILL_ACTION);

        Harness designer = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        designer.module.matrix().installExample();
        assertTrue(designer.forms.requireFill().allowed());
        assertEquals(designer.forms.requireWrite().reason(), designer.forms.requireFill().reason());

        Harness reader = Harness.open(Set.of(RoleMatrixCatalog.SELF_READER));
        reader.module.matrix().installExample();
        PermissionException fill = assertThrows(PermissionException.class, reader.forms::requireFill);
        PermissionException write = assertThrows(PermissionException.class, reader.forms::requireWrite);
        assertEquals("P2.PERM.0003", fill.errorCode().code());
        assertEquals("no_grant", fill.getMessage());
        assertEquals(write.errorCode().code(), fill.errorCode().code());
        assertEquals(write.getMessage(), fill.getMessage());
    }

    @Test
    void submitStartNeedsFormWriteAndProcessStart() {
        G2FormWriteHonesty.DualCheck catalog = G2FormWriteHonesty.catalog();
        assertEquals("form", catalog.fillResource());
        assertEquals("write", catalog.fillAction());
        assertEquals("process.instance", catalog.startResource());
        assertEquals("start", catalog.startAction());
        assertTrue(catalog.dualCheckRequired());
        assertFalse(catalog.formWriteOnly());
        assertFalse(catalog.processStartOnly());

        Harness approver = Harness.open(Set.of(RoleMatrixCatalog.APPROVER));
        approver.module.matrix().installExample();
        SurfaceGuard.require(approver.module.service(), catalog.startResource(), catalog.startAction());
        PermissionException approverFill = assertThrows(PermissionException.class, approver.forms::requireFill);
        assertEquals("P2.PERM.0003", approverFill.errorCode().code());
        assertEquals("no_grant", approverFill.getMessage());

        Harness designer = Harness.open(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        designer.module.matrix().installExample();
        designer.forms.requireFill();
        PermissionException designerStart = assertThrows(
                PermissionException.class,
                () -> SurfaceGuard.require(designer.module.service(), catalog.startResource(), catalog.startAction()));
        assertEquals("no_grant", designerStart.getMessage());
        assertEquals(approverFill.getMessage(), designerStart.getMessage());
    }

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        G2FormWriteHonesty.Inventory inventory = G2FormWriteHonesty.probeLive();
        assertEquals("SKIP", inventory.liveJarWalk());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.endpointSet() && inventory.issuedContextSet() && inventory.listenerPresent());
        }

        Map<String, String> report = G2FormWriteHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G2FormWriteHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g2_close"));
        assertEquals("PASS", report.get("form_write_surface"));
        assertEquals("PASS", report.get("submit_start_dual_check"));
        assertEquals("false", report.get("submit_start_is_form_write_only"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertEquals("false", report.get("redone_112"));
        assertEquals("false", report.get("redone_163"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G2FormWriteHonesty.verify(G2FormWriteHonesty.parseReport(fixtureFile("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveJarPass() {
        Map<String, String> fake = new HashMap<>(G2FormWriteHonesty.parseReport(fixtureFile("fake-live-jar-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2FormWriteHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_jar"));
    }

    @Test
    void honestyGateRejectsG2CloseClaim() {
        Map<String, String> fake = new HashMap<>(G2FormWriteHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("g2_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2FormWriteHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g2_close"));
    }

    @Test
    void honestyGateRejectsFormWriteOnlyAndSiblingRedos() {
        IllegalArgumentException fixtureOnly = assertThrows(IllegalArgumentException.class,
                () -> G2FormWriteHonesty.verify(G2FormWriteHonesty.parseReport(fixtureFile("fake-form-write-only.env"))));
        assertTrue(fixtureOnly.getMessage().contains("submit_start_is_form_write_only"));

        Map<String, String> fake = new HashMap<>(G2FormWriteHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("submit_start_is_form_write_only", "true");
        IllegalArgumentException only = assertThrows(IllegalArgumentException.class,
                () -> G2FormWriteHonesty.verify(fake));
        assertTrue(only.getMessage().contains("submit_start_is_form_write_only"));

        fake.put("submit_start_is_form_write_only", "false");
        fake.put("redone_112", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G2FormWriteHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("redone_112"));
    }

    @Test
    void docsReadmeKeepsBothLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g2-form-submission-write](./g2-form-submission-write.md)"));
        assertTrue(text.contains("[g2-submit-start-form-write](./g2-submit-start-form-write.md)"));
        assertTrue(text.contains("[g2-form-write-honesty](./g2-form-write-honesty.md)"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product g2 closed"));
        assertFalse(text.contains("产品级 G2 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g2-form-submission-write.md"));
        assertTrue(claims.contains("g2-submit-start-form-write.md"));
        assertTrue(claims.contains("g2-form-write-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G2 关闭"));
        assertTrue(claims.contains("不要重做 #112"));
        assertFalse(claims.contains("产品 G2 已关闭"));
    }

    private static Path fixtureFile(String name) {
        Path first = Path.of("scripts/g2-form-write-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g2-form-write-honesty/fixtures/" + name);
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private static String readExisting(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        return Files.readString(file);
    }

    private record Harness(PermissionModule module, FormSurfaceGuard forms) {
        static Harness open(Set<String> roles) {
            UUID id = UUID.fromString("33333333-3333-4333-8333-333333333333");
            TenantContextHolder.set(new TenantContext(
                    id, id, id, id, "g2-honesty", Instant.now(), Instant.now().plusSeconds(60)));
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
            PermissionModule module = new PermissionModule(
                    repository, subjects, snapshots, new InMemoryPolicyInvalidation());
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(null);
            return new Harness(module, new FormSurfaceGuard(module, provider));
        }
    }
}
