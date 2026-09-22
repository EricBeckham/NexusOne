package one.nexus.p3.studio;

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
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.infra.permission.FormSurfaceGuard;
import one.nexus.p3.studio.api.FormIsolationFilter;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DataScopeKind;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST {@code /forms/{id}/submissions} is {@code form.write} through
 * {@link FormSurfaceGuard}. Path {@code submit-start} with a {@code form_id}
 * is the same cell plus process start. Isolation-only {@link FormApiTest}
 * still has no P2 context.
 */
@SpringBootTest(classes = FormSubmissionWriteApiTest.TestApp.class)
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
class FormSubmissionWriteApiTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String FOREIGN_TENANT = "22222222-2222-4222-8222-222222222222";
    private static final String DENIED_ROLE = "g2_form_fill_denied";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    PermissionModule permission;

    @Autowired
    AtomicReference<Set<String>> roles;

    @Autowired
    AtomicReference<String> subjectTenant;

    @BeforeEach
    void bindIssued() {
        TenantContextHolder.set(new TenantContext(
                TENANT, TENANT, TENANT, TENANT, "form-fill", Instant.now(), Instant.now().plusSeconds(60)));
        roles.set(Set.of());
        subjectTenant.set(null);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        roles.set(Set.of());
        subjectTenant.set(null);
    }

    @Test
    void noGrantIs403BeforeSubmissionWrite() throws Exception {
        String id = publishLeaveForm("leave_fill_nogrant");
        roles.set(Set.of());
        postSubmission(id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        assertNoSubmissions(id);
    }

    @Test
    void explicitDenyIs403BeforeSubmissionWrite() throws Exception {
        String id = publishLeaveForm("leave_fill_deny");
        permission.matrix().installExample();
        if (permission.repository().findRoleByCode(TENANT, DENIED_ROLE).isEmpty()) {
            permission.admin().createRole(DENIED_ROLE, "G2 form fill explicit deny", false);
        }
        permission.admin().grant(
                "GRANT", "role", DENIED_ROLE, "form", "write", DataScopeKind.ALL, List.of(), Map.of());
        permission.admin().grant(
                "DENY", "role", DENIED_ROLE, "form", "write", DataScopeKind.ALL, List.of(), Map.of());
        roles.set(Set.of(DENIED_ROLE));
        postSubmission(id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0002"))
                .andExpect(jsonPath("$.message").value("explicit_deny"));
        assertNoSubmissions(id);
    }

    @Test
    void tenantBoundaryIs403BeforeSubmissionWrite() throws Exception {
        String id = publishLeaveForm("leave_fill_boundary");
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        subjectTenant.set(FOREIGN_TENANT);
        postSubmission(id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0001"))
                .andExpect(jsonPath("$.message").value("tenant_boundary"));
        subjectTenant.set(null);
        assertNoSubmissions(id);
    }

    @Test
    void grantedFormWriteCanSubmit() throws Exception {
        String id = publishLeaveForm("leave_fill_ok");
        postSubmission(id)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running_version").value(1))
                .andExpect(jsonPath("$.values.title").value("年假"));
        mvc.perform(get("/api/v1/p3/forms/" + id + "/submissions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].values.title").value("年假"));
    }

    @Test
    void selfReaderCanListSubmissionsButCannotFill() throws Exception {
        String id = publishLeaveForm("leave_fill_reader");
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.SELF_READER));
        mvc.perform(get("/api/v1/p3/forms/" + id + "/submissions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        postSubmission(id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P2.PERM.0003"))
                .andExpect(jsonPath("$.message").value("no_grant"));
        assertNoSubmissions(id);
    }

    private String publishLeaveForm(String formCode) throws Exception {
        permission.matrix().installExample();
        roles.set(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        mvc.perform(post("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app_code":"demo","form_code":"%s","name":"请假单",
                                 "bindings":[{"widget_code":"title","ledger_code":"leave_request","field_code":"title","required":true},
                                             {"widget_code":"days","ledger_code":"leave_request","field_code":"days","required":true,"widget_type":"NUMBER"},
                                             {"widget_code":"applicant","ledger_code":"leave_request","field_code":"applicant","required":true,"widget_type":"PERSON"}]}
                                """.formatted(formCode)))
                .andExpect(status().isCreated());
        MvcResult listed = mvc.perform(get("/api/v1/p3/forms")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andReturn();
        String id = null;
        for (JsonNode node : mapper.readTree(listed.getResponse().getContentAsString())) {
            if (formCode.equals(node.get("form_code").asText())) {
                id = node.get("form_id").asText();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(id);
        mvc.perform(post("/api/v1/p3/forms/" + id + "/validate")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/p3/forms/" + id + "/publish")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running_version").value(1));
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions postSubmission(String id) throws Exception {
        return mvc.perform(post("/api/v1/p3/forms/" + id + "/submissions")
                .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":{\"title\":\"年假\",\"days\":2,\"applicant\":\"alice\"}}"));
    }

    private void assertNoSubmissions(String id) throws Exception {
        roles.set(Set.of(RoleMatrixCatalog.FORM_DESIGNER));
        subjectTenant.set(null);
        mvc.perform(get("/api/v1/p3/forms/" + id + "/submissions")
                        .header(FormIsolationFilter.HEADER_DEMO_TENANT, TENANT.toString())
                        .header(FormIsolationFilter.HEADER_DEMO_ACTOR, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "one.nexus.p3.studio")
    static class TestApp {
        private final AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of());
        private final AtomicReference<String> subjectTenant = new AtomicReference<>();

        @Bean
        AtomicReference<Set<String>> formFillRoles() {
            return roles;
        }

        @Bean
        AtomicReference<String> formFillSubjectTenant() {
            return subjectTenant;
        }

        @Bean
        PermissionModule permissionModule() {
            InMemoryPolicyRepository repository = new InMemoryPolicyRepository();
            SubjectDirectory subjects = new SubjectDirectory() {
                @Override
                public Subject resolve(one.nexus.p2.kernel.TenantContext context) {
                    String tenant = subjectTenant.get() == null
                            ? context.tenantId().toString()
                            : subjectTenant.get();
                    return new Subject(
                            tenant,
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
