package one.nexus.p3.studio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.infra.permission.FormSurfaceGuard;
import one.nexus.p3.studio.api.FormIsolationFilter;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Form definition HTTP surfaces interpret {@code form.read} / {@code form.write}
 * through {@link FormSurfaceGuard}. Fill / POST submissions are covered by
 * {@link FormSubmissionWriteApiTest}. Isolation-only {@link FormApiTest} stays
 * tenant-only because it does not bind P2 TenantContext.
 */
@SpringBootTest(classes = FormStudioPermissionApiTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "nexus.studio.demo-context=true",
        "nexus.studio.persistence=memory",
        "nexus.studio.seed-demo-metadata=true",
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
class FormStudioPermissionApiTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    MockMvc mvc;

    @Autowired
    PermissionModule permission;

    @Autowired
    AtomicReference<Set<String>> roles;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "form-studio", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void listAndCreateAre403WithoutGrant() throws Exception {
        mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"leave_form","name":"请假单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
    }

    @Test
    void selfReaderCanListAndCannotCreate() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"leave_form","name":"请假单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"));
    }

    @Test
    void formDesignerCanCreateAndList() throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"leave_form","name":"请假单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft_status").value("DRAFT"));
        mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].form_code").value("leave_form"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "one.nexus.p3.studio")
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());

        @Bean
        AtomicReference<Set<String>> formStudioRoles() {
            return roles;
        }

        @Bean
        PermissionModule permissionModule() {
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(one.nexus.p2.kernel.TenantContext context) {
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
        FormSurfaceGuard formSurfaceGuard(PermissionModule module) {
            @SuppressWarnings("unchecked")
            ObjectProvider<TenantMemberGrantSeeder> provider = Mockito.mock(ObjectProvider.class);
            Mockito.when(provider.getIfAvailable()).thenReturn(null);
            return new FormSurfaceGuard(module, provider);
        }
    }
}
