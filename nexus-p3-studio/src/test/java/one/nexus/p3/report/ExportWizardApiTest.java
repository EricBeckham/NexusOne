package one.nexus.p3.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.infra.permission.ReportSurfaceGuard;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.surface.ReportSurfaceCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP export wizard begin / complete reuse ReportSurfaceGuard + AuthorizedExport.
 * Deny is 403 before IsolatedExport. Decision does not write.
 */
@SpringBootTest(classes = ExportWizardApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.flowable.spring.boot.ProcessEngineAutoConfiguration,"
                + "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration,"
                + "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration,"
                + "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration,"
                + "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.app.AppEngineAutoConfiguration,"
                + "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration,"
                + "org.flowable.spring.boot.RestApiAutoConfiguration,"
                + "org.flowable.spring.boot.FlowableSecurityAutoConfiguration,"
                + "org.flowable.spring.boot.FlowableJpaAutoConfiguration,"
                + "org.flowable.spring.boot.EndpointAutoConfiguration,"
                + "org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration"
})
class ExportWizardApiTest {
    private static final UUID TENANT = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String BEGIN_TRACE = "0199a1c0-0000-7000-8000-00000000c255";
    private static final String DENY_TRACE = "0199a1c0-0000-7000-8000-00000000c256";
    private static final String DECISION_TRACE = "0199a1c0-0000-7000-8000-00000000c257";
    private static final String COMPLETE_DENY_TRACE = "0199a1c0-0000-7000-8000-00000000c267";

    @Autowired
    MockMvc mvc;

    @Autowired
    PermissionModule permission;

    @Autowired
    InMemoryHashChainedAuditPort audits;

    @Autowired
    AtomicReference<Set<String>> roles;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "wizard-api", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
        TraceIds.clearMdc();
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void beginAndCompleteAre403WithoutGrant() throws Exception {
        TraceIds.putMdc(DENY_TRACE);
        mvc.perform(post("/api/v1/p3/exports/wizard/begin")
                        .header("X-Trace-Id", DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        mvc.perform(post("/api/v1/p3/exports/wizard/complete")
                        .header("X-Trace-Id", DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wizard_id\":\"missing\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
    }

    @Test
    void decisionShowsExportCellWithoutWriting() throws Exception {
        TraceIds.putMdc(DECISION_TRACE);
        mvc.perform(get("/api/v1/p3/exports/wizard/decision")
                        .header("X-Trace-Id", DECISION_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("PermissionEngine"))
                .andExpect(jsonPath("$.export.allowed").value(false))
                .andExpect(jsonPath("$.wizard").value(true))
                .andExpect(jsonPath("$.export_cell.resource").value(ReportSurfaceCatalog.EXPORT_RESOURCE))
                .andExpect(jsonPath("$.physical_dump").value(false));
        org.junit.jupiter.api.Assertions.assertEquals(0, audits.findByTraceId(DECISION_TRACE).size());
    }

    @Test
    void deptManagerBeginAndCompleteLandOnInMemoryChain() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.DEPT_MANAGER));
        bindAccess(TENANT.toString(), BEGIN_TRACE);
        TraceIds.putMdc(BEGIN_TRACE);
        MvcResult begun = mvc.perform(post("/api/v1/p3/exports/wizard/begin")
                        .header("X-Trace-Id", BEGIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"{\\\"surface\\\":\\\"export.wizard\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("begin"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.physical_dump").value(false))
                .andReturn();
        String wizardId = extract(begun.getResponse().getContentAsString(), "wizard_id");
        mvc.perform(post("/api/v1/p3/exports/wizard/complete")
                        .header("X-Trace-Id", BEGIN_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wizard_id\":\"" + wizardId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("complete"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.task_type").value(ReportSurfaceCatalog.EXPORT_RESOURCE));
        var found = audits.findByTraceId(BEGIN_TRACE);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())), () -> "missing begin: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType())), () -> "missing complete: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    @Test
    void completeAfterBeginDenyLandsPairAndSkipsIsolatedExport() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.DEPT_MANAGER));
        bindAccess(TENANT.toString(), COMPLETE_DENY_TRACE);
        TraceIds.putMdc(COMPLETE_DENY_TRACE);
        MvcResult begun = mvc.perform(post("/api/v1/p3/exports/wizard/begin")
                        .header("X-Trace-Id", COMPLETE_DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"{\\\"surface\\\":\\\"export.wizard\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("begin"))
                .andReturn();
        String wizardId = extract(begun.getResponse().getContentAsString(), "wizard_id");
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        mvc.perform(post("/api/v1/p3/exports/wizard/complete")
                        .header("X-Trace-Id", COMPLETE_DENY_TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wizard_id\":\"" + wizardId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        var found = audits.findByTraceId(COMPLETE_DENY_TRACE);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.EXPORT_BEGUN.equals(event.event().eventType())), () -> "missing begin: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.PERMISSION_DENIED.equals(event.event().eventType())), () -> "missing deny: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().anyMatch(event ->
                AuditCaptures.GUARD_REJECT.equals(event.event().eventType())), () -> "missing guard-reject: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().noneMatch(event ->
                AuditCaptures.EXPORT_COMPLETED.equals(event.event().eventType())), () -> "complete-deny wrote completed: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(found.stream().noneMatch(event ->
                AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType())), () -> "complete-deny wrote IsolatedExport: " + found);
        org.junit.jupiter.api.Assertions.assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }

    private void bindAccess(String tenantId, String trace) {
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("ops.summary"), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                trace));
    }

    private static String extract(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());
        private final InMemoryHashChainedAuditPort audits =
                new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));

        @Bean
        AtomicReference<Set<String>> wizardRoles() {
            return roles;
        }

        @Bean
        InMemoryHashChainedAuditPort wizardAudits() {
            return audits;
        }

        @Bean
        PermissionModule permissionModule() {
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(TenantContext context) {
                    return new Subject(
                            context.tenantId().toString(),
                            context.personId().toString(),
                            roles.get(),
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
            return new PermissionModule(repository, subjects, snapshots, new InMemoryPolicyInvalidation(), audits);
        }

        @Bean
        ReportSurfaceGuard reportSurfaceGuard(PermissionModule module) {
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(null);
            return new ReportSurfaceGuard(module, provider);
        }

        @Bean
        ExportWizardSessions exportWizardSessions() {
            return new ExportWizardSessions();
        }

        @Bean
        ExportWizardController exportWizardController(
                ReportSurfaceGuard guard, PermissionModule module, ExportWizardSessions sessions) {
            return new ExportWizardController(provider(guard), provider(module), sessions);
        }

        @Bean
        ReportApiExceptionHandler reportApiExceptionHandler() {
            return new ReportApiExceptionHandler();
        }

        @SuppressWarnings("unchecked")
        private static <T> ObjectProvider<T> provider(T bean) {
            ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(bean);
            return provider;
        }
    }
}
