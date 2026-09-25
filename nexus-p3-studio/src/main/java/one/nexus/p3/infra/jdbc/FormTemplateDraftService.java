package one.nexus.p3.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;

@Service
@ConditionalOnBean({IsolationGuard.class, AuditPort.class, ClockPort.class})
public class FormTemplateDraftService {
    private static final List<String> FIELD_TYPES = List.of(
            "TEXT", "TEXTAREA", "NUMBER", "MONEY", "DATE", "SELECT", "PERSON", "ATTACHMENT");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final UUID platformTenantId;

    public FormTemplateDraftService(JdbcTemplate jdbc, ObjectMapper mapper, IsolationGuard isolation, AuditPort audit,
            ClockPort clock, P1Properties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        isolation.requirePlatformContext("form-template.list", PlatformDuty.SYSTEM_ADMIN);
        List<UUID> ids = jdbc.query("SELECT id FROM platform.form_template ORDER BY updated_at DESC, id",
                (rs, row) -> rs.getObject(1, UUID.class));
        return ids.stream().map(this::getWithoutGuard).toList();
    }

    @Transactional
    public TemplateView create(TemplateWrite request) {
        PlatformContext platform = isolation.requirePlatformContext("form-template.create", PlatformDuty.SYSTEM_ADMIN);
        validate(request);
        UUID id = UUID.randomUUID();
        Instant now = clock.now();
        jdbc.update("""
                INSERT INTO platform.form_template (id, code, name, description, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, 1)
                """, id, request.code().trim(), request.name().trim(), request.description(), sqlTimestamp(now), sqlTimestamp(now));
        replaceFields(id, request.fields(), now);
        appendAudit(platform, "created", id, request.code().trim());
        return getWithoutGuard(id);
    }

    @Transactional(readOnly = true)
    public TemplateView get(UUID id) {
        isolation.requirePlatformContext("form-template.get", PlatformDuty.SYSTEM_ADMIN);
        return getWithoutGuard(id);
    }

    @Transactional
    public TemplateView update(UUID id, TemplateWrite request) {
        PlatformContext platform = isolation.requirePlatformContext("form-template.update", PlatformDuty.SYSTEM_ADMIN);
        validate(request);
        Instant now = clock.now();
        int changed = jdbc.update("""
                UPDATE platform.form_template
                   SET code = ?, name = ?, description = ?, status = 'DRAFT', updated_at = ?, version = version + 1
                 WHERE id = ?
                """, request.code().trim(), request.name().trim(), request.description(), sqlTimestamp(now), id);
        if (changed == 0) throw notFound();
        replaceFields(id, request.fields(), now);
        appendAudit(platform, "updated", id, request.code().trim());
        return getWithoutGuard(id);
    }

    @Transactional
    public ValidationResult validateDraft(UUID id) {
        PlatformContext platform = isolation.requirePlatformContext("form-template.validate", PlatformDuty.SYSTEM_ADMIN);
        Integer exists = jdbc.query("SELECT 1 FROM platform.form_template WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (exists == null) throw notFound();
        TemplateView template = getWithoutGuard(id);
        List<ValidationError> errors = FormTemplateDraftValidator.validate(template.fields());
        Instant now = clock.now();
        String outcome = errors.isEmpty() ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED;
        String status = errors.isEmpty() ? "VALIDATED" : template.status();
        if (errors.isEmpty()) {
            jdbc.update("UPDATE platform.form_template SET status = 'VALIDATED', updated_at = ?, version = version + 1 WHERE id = ?",
                    sqlTimestamp(now), id);
        }
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.form_template.validated", AuditEvent.VERSION, now,
                platform.auditTenantId(platformTenantId), platform.actor().subjectId(), platform.actorHomeTenantId(), true,
                platform.reason(), platform.traceId(), platform.traceId(), null, outcome, "form_template", id.toString(),
                Map.of("field_count", template.fields().size(), "error_count", errors.size())));
        return new ValidationResult(id, errors.isEmpty(), status, errors);
    }

    @Transactional
    public PublishResult publishDraft(UUID id) {
        PlatformContext platform = isolation.requirePlatformContext("form-template.publish", PlatformDuty.SYSTEM_ADMIN);
        String status = jdbc.query("SELECT status FROM platform.form_template WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (status == null) throw notFound();
        requireValidated(status);

        TemplateView template = getWithoutGuard(id);
        int nextVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM platform.form_template_version WHERE template_id = ?",
                Integer.class, id);
        UUID versionId = UUID.randomUUID();
        Instant publishedAt = clock.now();
        String snapshot;
        try {
            snapshot = mapper.writeValueAsString(template.fields());
        } catch (JacksonException e) {
            throw new IllegalStateException("could not serialize form template snapshot", e);
        }
        jdbc.update("""
                INSERT INTO platform.form_template_version
                    (id, template_id, version, fields_snapshot, published_at, published_by)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                """, versionId, id, nextVersion, snapshot, sqlTimestamp(publishedAt), platform.actor().subjectId());
        jdbc.update("""
                UPDATE platform.form_template
                   SET status = 'PUBLISHED', running_version_id = ?, updated_at = ?, version = version + 1
                 WHERE id = ?
                """, versionId, sqlTimestamp(publishedAt), id);
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.form_template.published", AuditEvent.VERSION,
                publishedAt, platform.auditTenantId(platformTenantId), platform.actor().subjectId(),
                platform.actorHomeTenantId(), true, platform.reason(), platform.traceId(), platform.traceId(), null,
                AuditEvent.OUTCOME_ACCEPTED, "form_template", id.toString(),
                Map.of("version_id", versionId.toString(), "version", nextVersion)));
        return new PublishResult(id, versionId, nextVersion, publishedAt, template.fields());
    }

    static void requireValidated(String status) {
        if (!"VALIDATED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "publish requires VALIDATED template status");
        }
    }

    private void replaceFields(UUID templateId, List<FieldWrite> fields, Instant now) {
        jdbc.update("DELETE FROM platform.form_template_field WHERE template_id = ?", templateId);
        for (int position = 0; position < fields.size(); position++) {
            FieldWrite field = fields.get(position);
            jdbc.update("""
                    INSERT INTO platform.form_template_field
                        (id, template_id, field_key, label, field_type, required, options, position, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                    """, UUID.randomUUID(), templateId, field.key().trim(), field.label().trim(), field.type(),
                    field.required(), json(field.options()), position, sqlTimestamp(now), sqlTimestamp(now));
        }
    }

    private TemplateView getWithoutGuard(UUID id) {
        TemplateRow row = jdbc.query("""
                SELECT id, code, name, description, status, running_version_id, created_at, updated_at, version
                  FROM platform.form_template WHERE id = ?
                """, rs -> rs.next() ? new TemplateRow(rs) : null, id);
        if (row == null) throw notFound();
        List<FieldView> fields = jdbc.query("""
                SELECT id, field_key, label, field_type, required, options::text, position
                  FROM platform.form_template_field WHERE template_id = ? ORDER BY position
                """, (rs, n) -> new FieldView(rs.getObject("id", UUID.class), rs.getString("field_key"),
                rs.getString("label"), rs.getString("field_type"), rs.getBoolean("required"),
                parseJson(rs.getString("options")), rs.getInt("position")), id);
        return new TemplateView(row.id, row.code, row.name, row.description, row.status,
                row.runningVersionId, row.createdAt, row.updatedAt, row.version, fields);
    }

    private void appendAudit(PlatformContext platform, String action, UUID id, String code) {
        Instant now = clock.now();
        // platform.audit_event.tenant_id is NOT NULL: prefer actor home tenant, else configured platform tenant.
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.form_template." + action, AuditEvent.VERSION, now,
                platform.auditTenantId(platformTenantId), platform.actor().subjectId(), platform.actorHomeTenantId(),
                true, platform.reason(), platform.traceId(), platform.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                "form_template", id.toString(), Map.of("code", code)));
    }

    private void validate(TemplateWrite request) {
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 160
                || request.fields() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code, name, and fields are required");
        }
        if (request.fields().size() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many fields");
        for (FieldWrite field : request.fields()) {
            if (field == null || field.key() == null || field.key().isBlank() || field.key().length() > 80
                    || field.label() == null || field.label().isBlank() || field.label().length() > 160
                    || !FIELD_TYPES.contains(field.type())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "each field requires key, label, and a supported type");
            }
        }
    }

    private String json(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalArgumentException("invalid field options", e); }
    }

    private JsonNode parseJson(String value) {
        if (value == null) return null;
        try { return mapper.readTree(value); }
        catch (JacksonException e) { throw new IllegalStateException("stored form template options are invalid", e); }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "form template not found");
    }

    public record TemplateWrite(String code, String name, String description, List<FieldWrite> fields) { }
    public record FieldWrite(String key, String label, String type, boolean required, JsonNode options) { }
    public record FieldView(UUID id, String key, String label, String type, boolean required, JsonNode options, int position) { }
    public record TemplateView(UUID id, String code, String name, String description, String status,
            UUID runningVersionId, Instant createdAt, Instant updatedAt, long version, List<FieldView> fields) { }
    public record ValidationError(UUID fieldId, String fieldKey, String code, String message) { }
    public record ValidationResult(UUID templateId, boolean valid, String status, List<ValidationError> errors) { }
    public record PublishResult(UUID templateId, UUID versionId, int version, Instant publishedAt, List<FieldView> fields) { }

    private static final class TemplateRow {
        private final UUID id, runningVersionId;
        private final String code, name, description, status;
        private final Instant createdAt, updatedAt;
        private final long version;
        private TemplateRow(ResultSet rs) throws SQLException {
            id = rs.getObject("id", UUID.class); code = rs.getString("code"); name = rs.getString("name");
            description = rs.getString("description"); status = rs.getString("status");
            runningVersionId = rs.getObject("running_version_id", UUID.class);
            createdAt = rs.getTimestamp("created_at").toInstant(); updatedAt = rs.getTimestamp("updated_at").toInstant();
            version = rs.getLong("version");
        }
    }
}
