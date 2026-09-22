package one.nexus.p3.flow.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.api.P3ApiExceptionHandler;
import one.nexus.p3.flow.TodoQueryService;
import one.nexus.p3.infra.permission.TodoSurfaceGuard;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Todo HTTP interprets {@code process.task}/{@code read} through
 * {@link TodoSurfaceGuard}. Deny happens before {@link TodoQueryService}.
 */
@SpringBootTest(classes = TodoSurfaceApiTest.TestApp.class)
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
class TodoSurfaceApiTest {
    private static final UUID TENANT = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID TODO = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @Autowired
    MockMvc mvc;

    @Autowired
    PermissionModule permission;

    @Autowired
    AtomicReference<Set<String>> roles;

    @Autowired
    TodoQueryService todos;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "todo-api", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
        Mockito.reset(todos);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void todoListAndDetailAre403WithoutGrantBeforeSinks() throws Exception {
        mvc.perform(get("/api/v1/p3/todos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        mvc.perform(get("/api/v1/todos/pending"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        mvc.perform(get("/api/v1/todos/done"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        mvc.perform(get("/api/v1/todos/initiated"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        mvc.perform(get("/api/v1/todos/" + TODO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        verify(todos, never()).pending();
        verify(todos, never()).done();
        verify(todos, never()).initiated();
        verify(todos, never()).get(TODO);
    }

    @Test
    void selfReaderCannotListTodos() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        mvc.perform(get("/api/v1/p3/todos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
        verify(todos, never()).pending();
    }

    @Test
    void approverCanListTodosOnSameEngine() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.APPROVER));
        when(todos.pending()).thenReturn(List.of());
        mvc.perform(get("/api/v1/p3/todos"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/todos/pending"))
                .andExpect(status().isOk());
        verify(todos, Mockito.times(2)).pending();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());

        @Bean
        AtomicReference<Set<String>> todoRoles() {
            return roles;
        }

        @Bean
        TodoQueryService todoQueryService() {
            return mock(TodoQueryService.class);
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
        TodoSurfaceGuard todoSurfaceGuard(PermissionModule module) {
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(null);
            return new TodoSurfaceGuard(module, provider);
        }

        @Bean
        TodoController todoController(TodoQueryService todos, TodoSurfaceGuard guard) {
            return new TodoController(todos, provider(guard));
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
