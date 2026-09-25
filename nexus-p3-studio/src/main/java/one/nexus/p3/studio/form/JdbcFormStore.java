package one.nexus.p3.studio.form;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcFormStore implements FormStore {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcFormStore(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void insert(FormRecord form) {
        jdbc.sql("""
                        INSERT INTO business_meta.form (
                          form_id, tenant_id, app_code, form_code, name, description,
                          draft_status, running_version, created_at, created_by, updated_at, updated_by)
                        VALUES (
                          :form_id, :tenant_id, :app_code, :form_code, :name, :description,
                          :draft_status, :running_version, :created_at, :created_by, :updated_at, :updated_by)
                        """)
                .param("form_id", form.formId())
                .param("tenant_id", form.tenantId())
                .param("app_code", form.appCode())
                .param("form_code", form.formCode())
                .param("name", form.name())
                .param("description", form.description())
                .param("draft_status", form.draftStatus().name())
                .param("running_version", form.runningVersion())
                .param("created_at", sqlTimestamp(form.createdAt()))
                .param("created_by", form.createdBy())
                .param("updated_at", sqlTimestamp(form.updatedAt()))
                .param("updated_by", form.updatedBy())
                .update();
        replaceBindings(form);
    }

    @Override
    public void update(FormRecord form) {
        jdbc.sql("""
                        UPDATE business_meta.form
                           SET name = :name,
                               description = :description,
                               draft_status = :draft_status,
                               running_version = :running_version,
                               updated_at = :updated_at,
                               updated_by = :updated_by
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                        """)
                .param("name", form.name())
                .param("description", form.description())
                .param("draft_status", form.draftStatus().name())
                .param("running_version", form.runningVersion())
                .param("updated_at", sqlTimestamp(form.updatedAt()))
                .param("updated_by", form.updatedBy())
                .param("form_id", form.formId())
                .param("tenant_id", form.tenantId())
                .update();
        replaceBindings(form);
    }

    @Override
    public Optional<FormRecord> find(UUID tenantId, UUID formId) {
        return jdbc.sql("""
                        SELECT form_id, tenant_id, app_code, form_code, name, description,
                               draft_status, running_version, created_at, created_by, updated_at, updated_by
                          FROM business_meta.form
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query((rs, row) -> mapForm(rs, loadBindings(formId)))
                .optional();
    }

    @Override
    public Optional<FormRecord> findByCode(UUID tenantId, String formCode) {
        return jdbc.sql("""
                        SELECT form_id, tenant_id, app_code, form_code, name, description,
                               draft_status, running_version, created_at, created_by, updated_at, updated_by
                          FROM business_meta.form
                         WHERE tenant_id = :tenant_id AND form_code = :form_code
                        """)
                .param("tenant_id", tenantId)
                .param("form_code", formCode)
                .query((rs, row) -> {
                    UUID formId = rs.getObject("form_id", UUID.class);
                    return mapForm(rs, loadBindings(formId));
                })
                .optional();
    }

    @Override
    public List<FormRecord> list(UUID tenantId) {
        return jdbc.sql("""
                        SELECT form_id, tenant_id, app_code, form_code, name, description,
                               draft_status, running_version, created_at, created_by, updated_at, updated_by
                          FROM business_meta.form
                         WHERE tenant_id = :tenant_id
                         ORDER BY updated_at DESC
                        """)
                .param("tenant_id", tenantId)
                .query((rs, row) -> {
                    UUID formId = rs.getObject("form_id", UUID.class);
                    return mapForm(rs, loadBindings(formId));
                })
                .list();
    }

    @Override
    public void insertVersion(FormVersion version) {
        jdbc.sql("""
                        INSERT INTO business_meta.form_version (
                          version_id, form_id, tenant_id, version_no, status, snapshot_json, notes, created_at, created_by)
                        VALUES (
                          :version_id, :form_id, :tenant_id, :version_no, :status, CAST(:snapshot AS jsonb),
                          :notes, :created_at, :created_by)
                        """)
                .param("version_id", version.versionId())
                .param("form_id", version.formId())
                .param("tenant_id", version.tenantId())
                .param("version_no", version.versionNo())
                .param("status", version.status().name())
                .param("snapshot", json(version.snapshot().toJson()))
                .param("notes", version.notes())
                .param("created_at", sqlTimestamp(version.createdAt()))
                .param("created_by", version.createdBy())
                .update();
    }

    @Override
    public void updateVersion(FormVersion version) {
        jdbc.sql("""
                        UPDATE business_meta.form_version
                           SET status = :status
                         WHERE version_id = :version_id AND tenant_id = :tenant_id
                        """)
                .param("status", version.status().name())
                .param("version_id", version.versionId())
                .param("tenant_id", version.tenantId())
                .update();
    }

    @Override
    public List<FormVersion> versions(UUID tenantId, UUID formId) {
        return jdbc.sql("""
                        SELECT version_id, form_id, tenant_id, version_no, status, snapshot_json, notes, created_at, created_by
                          FROM business_meta.form_version
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                         ORDER BY version_no
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query(this::mapVersion)
                .list();
    }

    @Override
    public Optional<FormVersion> findVersion(UUID tenantId, UUID formId, int versionNo) {
        return jdbc.sql("""
                        SELECT version_id, form_id, tenant_id, version_no, status, snapshot_json, notes, created_at, created_by
                          FROM business_meta.form_version
                         WHERE form_id = :form_id AND tenant_id = :tenant_id AND version_no = :version_no
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .param("version_no", versionNo)
                .query(this::mapVersion)
                .optional();
    }

    @Override
    public Optional<FormVersion> findRunning(UUID tenantId, UUID formId) {
        return jdbc.sql("""
                        SELECT version_id, form_id, tenant_id, version_no, status, snapshot_json, notes, created_at, created_by
                          FROM business_meta.form_version
                         WHERE form_id = :form_id AND tenant_id = :tenant_id AND status = 'RUNNING'
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query(this::mapVersion)
                .optional();
    }

    @Override
    public int nextVersionNo(UUID tenantId, UUID formId) {
        Integer max = jdbc.sql("""
                        SELECT COALESCE(MAX(version_no), 0) FROM business_meta.form_version
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query(Integer.class)
                .single();
        return max + 1;
    }

    @Override
    public void appendLog(PublishLog log) {
        jdbc.sql("""
                        INSERT INTO business_meta.form_publish_log (
                          log_id, form_id, tenant_id, version_no, from_status, to_status, outcome,
                          message, details, trace_id, occurred_at)
                        VALUES (
                          :log_id, :form_id, :tenant_id, :version_no, :from_status, :to_status, :outcome,
                          :message, CAST(:details AS jsonb), :trace_id, :occurred_at)
                        """)
                .param("log_id", log.logId())
                .param("form_id", log.formId())
                .param("tenant_id", log.tenantId())
                .param("version_no", log.versionNo())
                .param("from_status", log.fromStatus())
                .param("to_status", log.toStatus())
                .param("outcome", log.outcome())
                .param("message", log.message())
                .param("details", json(log.details() == null ? Map.of() : log.details()))
                .param("trace_id", log.traceId())
                .param("occurred_at", sqlTimestamp(log.occurredAt()))
                .update();
    }

    @Override
    public List<PublishLog> logs(UUID tenantId, UUID formId) {
        return jdbc.sql("""
                        SELECT log_id, form_id, tenant_id, version_no, from_status, to_status, outcome,
                               message, details, trace_id, occurred_at
                          FROM business_meta.form_publish_log
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                         ORDER BY occurred_at
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query((rs, row) -> new PublishLog(
                        rs.getObject("log_id", UUID.class),
                        rs.getObject("form_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        (Integer) rs.getObject("version_no"),
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("outcome"),
                        rs.getString("message"),
                        readMap(rs.getString("details")),
                        rs.getString("trace_id"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    @Override
    public void insertSubmission(FormSubmission submission) {
        jdbc.sql("""
                        INSERT INTO business_meta.form_submission (
                          submission_id, form_id, tenant_id, running_version, values_json,
                          submitted_at, submitted_by, ledger_id, ledger_record_id)
                        VALUES (
                          :submission_id, :form_id, :tenant_id, :running_version, CAST(:values AS jsonb),
                          :submitted_at, :submitted_by, :ledger_id, :ledger_record_id)
                        """)
                .param("submission_id", submission.submissionId())
                .param("form_id", submission.formId())
                .param("tenant_id", submission.tenantId())
                .param("running_version", submission.runningVersion())
                .param("values", json(submission.values()))
                .param("submitted_at", sqlTimestamp(submission.submittedAt()))
                .param("submitted_by", submission.submittedBy())
                .param("ledger_id", submission.ledgerId())
                .param("ledger_record_id", submission.ledgerRecordId())
                .update();
    }

    @Override
    public List<FormSubmission> submissions(UUID tenantId, UUID formId) {
        return jdbc.sql("""
                        SELECT submission_id, form_id, tenant_id, running_version, values_json,
                               submitted_at, submitted_by, ledger_id, ledger_record_id
                          FROM business_meta.form_submission
                         WHERE form_id = :form_id AND tenant_id = :tenant_id
                         ORDER BY submitted_at DESC
                        """)
                .param("form_id", formId)
                .param("tenant_id", tenantId)
                .query(this::mapSubmission)
                .list();
    }

    @Override
    public Optional<FormSubmission> findByLedgerRecord(UUID tenantId, UUID ledgerRecordId) {
        return jdbc.sql("""
                        SELECT submission_id, form_id, tenant_id, running_version, values_json,
                               submitted_at, submitted_by, ledger_id, ledger_record_id
                          FROM business_meta.form_submission
                         WHERE tenant_id = :tenant_id AND ledger_record_id = :record_id
                         ORDER BY submitted_at DESC
                         LIMIT 1
                        """)
                .param("tenant_id", tenantId)
                .param("record_id", ledgerRecordId)
                .query(this::mapSubmission)
                .optional();
    }

    private FormSubmission mapSubmission(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new FormSubmission(
                rs.getObject("submission_id", UUID.class),
                rs.getObject("form_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getInt("running_version"),
                readMap(rs.getString("values_json")),
                rs.getTimestamp("submitted_at").toInstant(),
                rs.getString("submitted_by"),
                rs.getObject("ledger_id", UUID.class),
                rs.getObject("ledger_record_id", UUID.class));
    }

    private void replaceBindings(FormRecord form) {
        jdbc.sql("DELETE FROM business_meta.form_binding WHERE form_id = :form_id")
                .param("form_id", form.formId())
                .update();
        for (FormBinding binding : form.draftBindings()) {
            jdbc.sql("""
                            INSERT INTO business_meta.form_binding (
                              binding_id, form_id, tenant_id, widget_code, ledger_code, field_code,
                              required, readonly, widget_type, ordinal)
                            VALUES (
                              :binding_id, :form_id, :tenant_id, :widget_code, :ledger_code, :field_code,
                              :required, :readonly, :widget_type, :ordinal)
                            """)
                    .param("binding_id", one.nexus.common.id.UuidV7.generate())
                    .param("form_id", form.formId())
                    .param("tenant_id", form.tenantId())
                    .param("widget_code", binding.widgetCode())
                    .param("ledger_code", binding.ledgerCode())
                    .param("field_code", binding.fieldCode())
                    .param("required", binding.required())
                    .param("readonly", binding.readonly())
                    .param("widget_type", binding.widgetType().name())
                    .param("ordinal", binding.ordinal())
                    .update();
        }
    }

    private List<FormBinding> loadBindings(UUID formId) {
        return jdbc.sql("""
                        SELECT widget_code, ledger_code, field_code, required, readonly, widget_type, ordinal
                          FROM business_meta.form_binding
                         WHERE form_id = :form_id
                         ORDER BY ordinal, widget_code
                        """)
                .param("form_id", formId)
                .query((rs, row) -> new FormBinding(
                        rs.getString("widget_code"),
                        rs.getString("ledger_code"),
                        rs.getString("field_code"),
                        rs.getBoolean("required"),
                        rs.getBoolean("readonly"),
                        WidgetType.valueOf(rs.getString("widget_type")),
                        rs.getInt("ordinal")))
                .list();
    }

    private FormRecord mapForm(java.sql.ResultSet rs, List<FormBinding> bindings) throws java.sql.SQLException {
        Integer running = (Integer) rs.getObject("running_version");
        return new FormRecord(
                rs.getObject("form_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("app_code"),
                rs.getString("form_code"),
                rs.getString("name"),
                rs.getString("description"),
                FormStatus.valueOf(rs.getString("draft_status")),
                running,
                bindings,
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by"));
    }

    private FormVersion mapVersion(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Map<String, Object> snapshot = readMap(rs.getString("snapshot_json"));
        return new FormVersion(
                rs.getObject("version_id", UUID.class),
                rs.getObject("form_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getInt("version_no"),
                VersionStatus.valueOf(rs.getString("status")),
                FormSnapshot.fromJson(snapshot),
                rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"));
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private Map<String, Object> readMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = mapper.readValue(raw, MAP);
        Object bindings = parsed.get("bindings");
        if (bindings instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
            parsed.put("bindings", mapper.convertValue(bindings, MAP_LIST));
        }
        return parsed;
    }
}
