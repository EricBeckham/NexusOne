package one.nexus.p3.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Creates a tenant application against the immutable template/process versions pinned at install time. */
@Service
public class ProductApplicationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PinnedProductApplicationValidationService validation;
    private final SequentialApprovalService approvals;
    private final ObjectProvider<AuditPort> auditPort;
    private final ObjectProvider<ApprovalCommentStore> comments;

    public ProductApplicationService(JdbcTemplate jdbc, ObjectMapper mapper,
            PinnedProductApplicationValidationService validation, SequentialApprovalService approvals,
            ObjectProvider<AuditPort> auditPort, ObjectProvider<ApprovalCommentStore> comments) {
        this.jdbc = jdbc;
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
        return jdbc.query("""
                SELECT a.id, a.installation_id, a.created_at, a.process_instance_id,
                       COALESCE(i.status, a.status) AS status
                  FROM business_meta.product_application a
                  LEFT JOIN business_meta.process_instance i
                    ON i.id = a.process_instance_id AND i.tenant_id = a.tenant_id
                 WHERE a.tenant_id = ? AND a.initiator_person_id = ?
                 ORDER BY a.created_at DESC
                """, (rs, row) -> new ProductApplicationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("installation_id", UUID.class),
                        rs.getString("status"),
                        commentFor(iamTenant.tenantId(), rs.getObject("process_instance_id", UUID.class)),
                        rs.getTimestamp("created_at").toInstant()),
                iamTenant.tenantId(), iamTenant.personId());
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

        PinnedProcess pin = jdbc.query("""
                SELECT p.process_definition_key, p.process_definition_version
                  FROM market.installation_product_pin p
                  JOIN market.installation i ON i.id=p.installation_id
                 WHERE p.installation_id=? AND i.tenant_id=? AND i.status='INSTALLED'
                """, rs -> rs.next() ? new PinnedProcess(rs.getString(1), (Integer) rs.getObject(2)) : null,
                installationId, tenantId);
        if (pin == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "installed product not found");
        if (pin.key() == null || pin.version() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "installed product has no pinned approval process");
        }

        String personField = jdbc.query("SELECT fields_snapshot::text FROM platform.form_template_version WHERE id=?",
                rs -> rs.next() ? personField(rs.getString(1)) : null, result.formTemplateVersionId());
        if (personField == null || values == null || values.get(personField) == null || !values.get(personField).isTextual()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "a PERSON field is required for the approver");
        }

        UUID id = UuidV7.generate();
        Instant now = Utc.now();
        String businessKey = "product-application:" + id;
        jdbc.update("""
                INSERT INTO business_meta.product_application
                    (id, tenant_id, installation_id, product_definition_id, form_template_version_id,
                     process_instance_id, values_json, status, initiator_person_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, CAST(? AS jsonb), 'RUNNING', ?, ?, ?)
                """, id, tenantId, installationId, result.productDefinitionId(), result.formTemplateVersionId(),
                writeValues(values), iamTenant.personId(), now, now);
        ProcessInstanceView process = approvals.start(new StartCommand(pin.key(), businessKey, AssigneeType.PERSON,
                values.get(personField).asText()), pin.version());
        jdbc.update("UPDATE business_meta.product_application SET process_instance_id=?, updated_at=? WHERE id=? AND tenant_id=?",
                process.id(), Utc.now(), id, tenantId);

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
        String taskId = jdbc.query("""
                SELECT flowable_task_id FROM business_meta.process_todo
                 WHERE tenant_id = ? AND instance_id = ? AND outcome IS NOT NULL
                 ORDER BY acted_at DESC NULLS LAST
                 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, tenantId, instanceId);
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
