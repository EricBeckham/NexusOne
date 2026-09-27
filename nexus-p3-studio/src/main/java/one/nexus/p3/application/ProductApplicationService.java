package one.nexus.p3.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.common.time.Utc;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ApprovalCommentStore;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.StartCommand;
import one.nexus.p3.flow.SequentialApprovalService;
import one.nexus.p3.port.ProductApplicationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Creates a tenant application against the immutable template/process versions pinned at install time. */
@Service
public class ProductApplicationService {
    private final ProductApplicationStore store;
    private final ObjectMapper mapper;
    private final PinnedProductApplicationValidationService validation;
    private final SequentialApprovalService approvals;
    private final ObjectProvider<AuditPort> auditPort;
    private final ObjectProvider<ApprovalCommentStore> comments;

    public ProductApplicationService(ProductApplicationStore store, ObjectMapper mapper,
            PinnedProductApplicationValidationService validation, SequentialApprovalService approvals,
            ObjectProvider<AuditPort> auditPort, ObjectProvider<ApprovalCommentStore> comments) {
        this.store = store;
        this.mapper = mapper;
        this.validation = validation;
        this.approvals = approvals;
        this.auditPort = auditPort;
        this.comments = comments;
    }

    @Transactional(readOnly = true)
    public List<ProductApplicationSummary> listMine() {
        TenantContext accessTenant = requireAccessTenant();
        one.nexus.p2.kernel.TenantContext iamTenant = TenantContextHolder.get();
        if (iamTenant == null || !accessTenant.tenantId().equals(iamTenant.tenantId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "matching tenant context is required");
        }
        return store.listByInitiator(iamTenant.tenantId(), iamTenant.personId()).stream()
                .map(row -> new ProductApplicationSummary(row.id(), row.installationId(), row.status(),
                        commentFor(iamTenant.tenantId(), row.processInstanceId()), row.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FormSnapshot form(UUID installationId) {
        TenantContext accessTenant = requireAccessTenant();
        one.nexus.p2.kernel.TenantContext iamTenant = TenantContextHolder.get();
        if (iamTenant == null || !accessTenant.tenantId().equals(iamTenant.tenantId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "matching tenant context is required");
        }
        UUID versionId = store.findInstalledPin(iamTenant.tenantId(), installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installed product not found"))
                .formTemplateVersionId();
        if (versionId == null) return new FormSnapshot(installationId, null, List.of());
        String snapshot = store.formTemplateSnapshot(versionId).orElse(null);
        if (snapshot == null || snapshot.isBlank()) return new FormSnapshot(installationId, versionId, List.of());
        return new FormSnapshot(installationId, versionId, parseFormFields(snapshot));
    }

    @Transactional
    public ProductApplicationView create(UUID installationId, JsonNode values) {
        TenantContext accessTenant = requireAccessTenant();
        one.nexus.p2.kernel.TenantContext iamTenant = TenantContextHolder.get();
        if (iamTenant == null || !accessTenant.tenantId().equals(iamTenant.tenantId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "matching tenant context is required");
        }
        UUID tenantId = UUID.fromString(accessTenant.tenantId());
        var result = validation.validate(installationId, values);
        if (!result.valid()) throw new InvalidProductApplicationException(result.errors());

        ProductApplicationStore.InstalledPin installed = store.findInstalledPin(tenantId, installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installed product not found"));
        var override = store.findProcessOverride(tenantId, installationId);
        PinnedProcess pin = override.map(value -> new PinnedProcess(value.key(), value.version()))
                .orElseGet(() -> new PinnedProcess(installed.processDefinitionKey(), installed.processDefinitionVersion()));
        if (pin.key() == null || pin.version() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "installed product has no pinned approval process");
        }

        String personField = store.formTemplateSnapshot(result.formTemplateVersionId())
                .map(ProductApplicationService::personField).orElse(null);
        if (personField == null || values == null || values.get(personField) == null || !values.get(personField).isTextual()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "a PERSON field is required for the approver");
        }

        UUID id = UuidV7.generate();
        Instant now = Utc.now();
        String businessKey = "product-application:" + id;
        store.insertRunning(new ProductApplicationStore.NewApplication(id, tenantId, installationId,
                result.productDefinitionId(), result.formTemplateVersionId(), writeValues(values),
                iamTenant.personId(), now));
        ProcessInstanceView process = approvals.startProduct(new StartCommand(pin.key(), businessKey, AssigneeType.PERSON,
                values.get(personField).asText()), pin.version(), override.isPresent());
        store.attachProcessInstance(tenantId, id, process.id(), Utc.now());

        AuditPort port = auditPort.getIfAvailable();
        if (port != null) port.append(AuditCaptures.data("p3.product_application.started", true, tenantId,
                iamTenant.personId(), "product_application", id.toString(), "product application started",
                accessTenant.traceId(), Map.of("installation_id", installationId.toString(),
                        "process_instance_id", process.id().toString(), "process_key", pin.key(),
                        "process_version", pin.version().toString())));
        return new ProductApplicationView(id, tenantId, installationId, result.productDefinitionId(),
                result.formTemplateVersionId(), process.id(), "RUNNING", now);
    }

    private static String personField(String snapshot) {
        try {
            JsonNode fields = new ObjectMapper().readTree(snapshot);
            if (fields != null && fields.isArray()) for (JsonNode field : fields) {
                if (field.path("type").asText().equals("PERSON")) return field.path("key").asText(null);
            }
            return null;
        } catch (Exception ex) {
            throw new IllegalStateException("pinned template snapshot is invalid JSON", ex);
        }
    }

    private String writeValues(JsonNode values) {
        try { return mapper.writeValueAsString(values); }
        catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalStateException("cannot serialize product application values", ex);
        }
    }

    private String commentFor(UUID tenantId, UUID instanceId) {
        if (instanceId == null) return null;
        String taskId = store.latestActedTaskId(tenantId, instanceId).orElse(null);
        ApprovalCommentStore store = comments.getIfAvailable();
        if (taskId == null || store == null) return null;
        return store.latest(taskId);
    }

    private static TenantContext requireAccessTenant() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        return tenant;
    }

    public record ProductApplicationView(UUID id, UUID tenantId, UUID installationId, UUID productDefinitionId,
            UUID formTemplateVersionId, UUID processInstanceId, String status, Instant createdAt) { }
    public record ProductApplicationSummary(UUID id, UUID installationId, String status, String comment, Instant createdAt) { }
    public record FormSnapshot(UUID installationId, UUID formTemplateVersionId, List<FormField> fields) { }
    public record FormField(String key, String label, String type, boolean required, List<String> options) { }

    private List<FormField> parseFormFields(String snapshot) {
        try {
            JsonNode array = mapper.readTree(snapshot);
            if (array == null || !array.isArray()) return List.of();
            List<FormField> fields = new ArrayList<>();
            for (JsonNode node : array) {
                List<String> options = new ArrayList<>();
                JsonNode choices = node.get("options");
                if (choices != null && choices.isArray()) for (JsonNode choice : choices) {
                    if (choice.isTextual()) options.add(choice.asText());
                    else if (choice.isObject() && choice.path("value").isTextual()) options.add(choice.path("value").asText());
                }
                String key = node.path("key").asText("");
                fields.add(new FormField(key, node.path("label").asText(key), node.path("type").asText("TEXT"),
                        node.path("required").asBoolean(false), List.copyOf(options)));
            }
            return List.copyOf(fields);
        } catch (JacksonException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "pinned form template snapshot is missing");
        }
    }
    private record PinnedProcess(String key, Integer version) { }
    public static final class InvalidProductApplicationException extends RuntimeException {
        private final java.util.List<ProductApplicationValuesValidator.FieldError> errors;
        public InvalidProductApplicationException(java.util.List<ProductApplicationValuesValidator.FieldError> errors) {
            super("application values do not match the pinned form template");
            this.errors = java.util.List.copyOf(errors);
        }
        public java.util.List<ProductApplicationValuesValidator.FieldError> errors() { return errors; }
    }
}
