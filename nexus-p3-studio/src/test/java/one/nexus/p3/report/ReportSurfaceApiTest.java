package one.nexus.p3.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.application.LedgerRecordService;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore.Page;
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

/**
 * Report HTTP surfaces interpret {@code ledger/read} and
 * {@code ledger.export.csv/submit} through {@link ReportSurfaceGuard}.
 * Deny happens before ledger list / IsolatedExport sinks.
 */
@SpringBootTest(classes = ReportSurfaceApiTest.TestApp.class)
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
class ReportSurfaceApiTest {
    private static final UUID TENANT = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID LEDGER = UUID.fromString("66666666-6666-4666-8666-666666666666");

    @Autowired
    MockMvc mvc;

    @Autowired
    PermissionModule permission;

    @Autowired
    AtomicReference<Set<String>> roles;

    @Autowired
    LedgerRecordService records;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "report-api", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
        Mockito.reset(records);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
    }

    @Test
    void queryAndExportAre403WithoutGrantBeforeSinks() throws Exception {
        mvc.perform(post("/api/v1/p3/reports/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledger_id\":\"" + LEDGER + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        mvc.perform(post("/api/v1/p3/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        verify(records, never()).list(any(), any(), anyInt(), anyInt());
    }

    @Test
    void decisionShowsSameCellsWithoutThrowing() throws Exception {
        mvc.perform(get("/api/v1/p3/reports/decision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("PermissionEngine"))
                .andExpect(jsonPath("$.query.allowed").value(false))
                .andExpect(jsonPath("$.query.reason").value("no_grant"))
                .andExpect(jsonPath("$.export.allowed").value(false))
                .andExpect(jsonPath("$.export_cell.resource").value(ReportSurfaceCatalog.EXPORT_RESOURCE))
                .andExpect(jsonPath("$.physical_dump").value("open"));
    }

    @Test
    void selfReaderCanQueryAndCannotExport() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        when(records.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new Page(List.of(Map.of("id", "r1")), 1, 0, 20));
        mvc.perform(post("/api/v1/p3/reports/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledger_id\":\"" + LEDGER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("PermissionEngine"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.decision.engine").value("PermissionEngine"));
        mvc.perform(post("/api/v1/p3/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
    }

    @Test
    void deptManagerExportRunsAuthorizedExportNotPhysicalDump() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.DEPT_MANAGER));
        bindAccess(TENANT.toString());
        mvc.perform(post("/api/v1/p3/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"{\\\"ledger\\\":\\\"leave\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("PermissionEngine"))
                .andExpect(jsonPath("$.task_type").value(ReportSurfaceCatalog.EXPORT_RESOURCE))
                .andExpect(jsonPath("$.physical_dump").value(false));
    }

    private void bindAccess(String tenantId) {
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry(), Set.of("ops.summary"), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-report"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());

        @Bean
        AtomicReference<Set<String>> reportRoles() {
            return roles;
        }

        @Bean
        LedgerRecordService ledgerRecordService() {
            return mock(LedgerRecordService.class);
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
            return new PermissionModule(repository, subjects, snapshots, new InMemoryPolicyInvalidation());
        }

        @Bean
        ReportSurfaceGuard reportSurfaceGuard(PermissionModule module) {
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(null);
            return new ReportSurfaceGuard(module, provider);
        }

        @Bean
        ReportController reportController(
                ReportSurfaceGuard guard, LedgerRecordService records, PermissionModule module) {
            return new ReportController(provider(guard), provider(records), provider(module));
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
