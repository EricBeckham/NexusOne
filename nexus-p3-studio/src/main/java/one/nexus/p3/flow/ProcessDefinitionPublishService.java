package one.nexus.p3.flow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes tenant-owned drafts as tenant-scoped Flowable deployments. */
@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionPublishService {
    private final RepositoryService repositoryService;
    private final ProcessDefinitionDraftService drafts;
    private final ProcessDefinitionValidationService validation;
    private final ObjectProvider<AuditPort> auditPort;

    public ProcessDefinitionPublishService(
            RepositoryService repositoryService,
            ProcessDefinitionDraftService drafts,
            ProcessDefinitionValidationService validation,
            ObjectProvider<AuditPort> auditPort
    ) {
        this.repositoryService = repositoryService;
        this.drafts = drafts;
        this.validation = validation;
        this.auditPort = auditPort;
    }

    @Transactional
    public PublishedDefinition publish(String key) {
        TenantContext tenant = requireTenant();
        ProcessDefinitionDraft draft = drafts.get(key);
        ProcessDefinitionValidationResult result = validation.validateDraft(key);
        if (!result.valid()) {
            throw FlowException.badRequest("process draft must pass validation before publishing");
        }

        String resourceName = key + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name("tenant-process-" + tenant.tenantId() + "-" + key)
                .tenantId(tenant.tenantId().toString())
                .addString(resourceName, draft.bpmnXml())
                .deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionKey(key)
                .singleResult();
        if (definition == null) {
            throw FlowException.conflict("Flowable did not create the published process definition");
        }
        AuditPort port = auditPort.getIfAvailable();
        if (port != null) {
            port.append(AuditCaptures.capture(
                    AuditEvent.OUTCOME_ACCEPTED, AuditCategory.APPROVAL, "p3.process.definition.publish",
                    tenant.tenantId(), tenant.personId(), tenant.tenantId(), false,
                    null, null, "process_definition", key,
                    Map.of("version", definition.getVersion(), "deployment_id", deployment.getId())));
        }
        return view(definition, deployment);
    }

    @Transactional(readOnly = true)
    public PublishedDefinition get(String key) {
        TenantContext tenant = requireTenant();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(key)
                .processDefinitionTenantId(tenant.tenantId().toString())
                .latestVersion()
                .singleResult();
        if (definition == null) {
            throw FlowException.notFound("published process definition not found in current tenant");
        }
        Deployment deployment = repositoryService.createDeploymentQuery()
                .deploymentId(definition.getDeploymentId()).singleResult();
        return view(definition, deployment);
    }

    @Transactional(readOnly = true)
    public List<PublishedDefinition> list() {
        TenantContext tenant = requireTenant();
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(tenant.tenantId().toString())
                .latestVersion()
                .list();
        List<PublishedDefinition> published = new ArrayList<>();
        for (ProcessDefinition definition : definitions) {
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(definition.getDeploymentId()).singleResult();
            published.add(view(definition, deployment));
        }
        published.sort(Comparator.comparing(PublishedDefinition::key));
        return List.copyOf(published);
    }

    /** Read-only existence check for platform product binding; caller must enforce platform duty. */
    @Transactional(readOnly = true)
    public boolean isPublishedVersionAvailable(String key, int version) {
        return !repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(key)
                .processDefinitionVersion(version)
                .list()
                .isEmpty();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> definition(String key) {
        PublishedDefinition published = get(key);
        try (InputStream input = repositoryService.getResourceAsStream(
                published.deploymentId(), published.resourceName())) {
            if (input == null) throw FlowException.notFound("published process resource not found");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input.transferTo(output);
            return ProcessDefinitionCatalog.read(output.toString(StandardCharsets.UTF_8), "tenant-deployment");
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read published process definition", ex);
        }
    }

    private static PublishedDefinition view(ProcessDefinition definition, Deployment deployment) {
        return new PublishedDefinition(
                definition.getKey(), definition.getName(), definition.getTenantId(), definition.getVersion(),
                definition.getId(), definition.getDeploymentId(),
                deployment == null ? null : deployment.getDeploymentTime(),
                definition.getResourceName());
    }

    private static TenantContext requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) throw FlowException.missingContext();
        return context;
    }

    public record PublishedDefinition(
            String key, String name, String tenantId, int version, String processDefinitionId,
            String deploymentId, java.util.Date publishedAt, String resourceName) {}
}
