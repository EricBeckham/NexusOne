package one.nexus.p3.api;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.application.ApplicationService;
import one.nexus.p3.infra.permission.CatalogSurfaceGuard;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.identity.SubjectDirectory;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
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
 * Catalog HTTP interprets {@code ledger/read} and {@code ledger/write} through
 * {@link CatalogSurfaceGuard}. Deny happens before application/ledger sinks.
 */
@SpringBootTest(classes = CatalogSurfaceApiTest.TestApp.class)
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
class CatalogSurfaceApiTest {
    private static final UUID TENANT = UUID.fromString("77777777-7777-4777-8777-777777777777");

    @Autowired
    MockMvc mvc;

    @Autowired
    PermissionModule permission;

    @Autowired
    AtomicReference<Set<String>> roles;

    @Autowired
    ApplicationService applications;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "catalog-api", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
        Mockito.reset(applications);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void catalogListAndCreateAre403WithoutGrantBeforeSinks() throws Exception {
        mvc.perform(get("/api/v1/p3/applications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        mvc.perform(post("/api/v1/p3/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_code\":\"hr\",\"display_name\":\"HR\",\"kind\":\"CUSTOM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        verify(applications, never()).list();
        verify(applications, never()).create(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void selfReaderCanListAndCannotCreate() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        when(applications.list()).thenReturn(List.of());
        mvc.perform(get("/api/v1/p3/applications"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/p3/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_code\":\"hr\",\"display_name\":\"HR\",\"kind\":\"CUSTOM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        verify(applications).list();
        verify(applications, never()).create(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());

        @Bean
        AtomicReference<Set<String>> catalogRoles() {
            return roles;
        }

        @Bean
        ApplicationService applicationService() {
            return mock(ApplicationService.class);
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
        CatalogSurfaceGuard catalogSurfaceGuard(PermissionModule module) {
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(null);
            return new CatalogSurfaceGuard(module, provider);
        }

        @Bean
        ApplicationController applicationController(ApplicationService applications, CatalogSurfaceGuard guard) {
            return new ApplicationController(provider(applications), provider(guard));
        }

        @Bean
        P3ApiExceptionHandler p3ApiExceptionHandler() {
            return new P3ApiExceptionHandler();
        }

        @SuppressWarnings("unchecked")
        private static <T> ObjectProvider<T> provider(T bean) {
            ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(bean);
            return provider;
        }
    }
}
