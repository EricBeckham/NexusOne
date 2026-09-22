package one.nexus.p3.studio.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.studio.form.BindingValidator;
import one.nexus.p3.studio.form.FormBinding;
import one.nexus.p3.studio.form.FormRecord;
import one.nexus.p3.studio.form.FormService;
import one.nexus.p3.studio.form.FormSubmission;
import one.nexus.p3.studio.form.FormVersion;
import one.nexus.p3.studio.form.PublishLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/p3/forms")
public class FormController {
    private final FormService forms;

    public FormController(FormService forms) {
        this.forms = forms;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody UpsertRequest request) {
        List<FormBinding> bindings = BindingValidator.parse(request.bindings());
        return toJson(forms.create(request.appCode(), request.formCode(), request.name(), request.description(), bindings));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return forms.list().stream().map(FormController::toJson).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return toJson(forms.get(id));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpsertRequest request) {
        List<FormBinding> bindings = request.bindings() == null ? null : BindingValidator.parse(request.bindings());
        return toJson(forms.update(id, request.name(), request.description(), bindings));
    }

    @PostMapping("/{id}/validate")
    public Map<String, Object> validate(@PathVariable UUID id) {
        return toJson(forms.validate(id));
    }

    @PostMapping("/{id}/publish")
    public Map<String, Object> publish(@PathVariable UUID id, @RequestBody(required = false) PublishRequest request) {
        String notes = request == null ? null : request.notes();
        return toJson(forms.publish(id, notes));
    }

    @GetMapping("/{id}/running")
    public Map<String, Object> running(@PathVariable UUID id) {
        return versionJson(forms.running(id));
    }

    @GetMapping("/{id}/versions")
    public List<Map<String, Object>> versions(@PathVariable UUID id) {
        return forms.versions(id).stream().map(FormController::versionJson).toList();
    }

    @GetMapping("/{id}/publish-log")
    public List<Map<String, Object>> publishLog(@PathVariable UUID id) {
        return forms.publishLog(id).stream().map(FormController::logJson).toList();
    }

    @PostMapping("/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(@PathVariable UUID id, @RequestBody(required = false) SubmitRequest request) {
        Map<String, Object> values = request == null || request.values() == null ? Map.of() : request.values();
        return submissionJson(forms.submit(id, values));
    }

    @GetMapping("/{id}/submissions")
    public List<Map<String, Object>> submissions(@PathVariable UUID id) {
        return forms.submissions(id).stream().map(FormController::submissionJson).toList();
    }

    private static Map<String, Object> toJson(FormRecord form) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("form_id", form.formId().toString());
        json.put("tenant_id", form.tenantId().toString());
        json.put("app_code", form.appCode());
        json.put("form_code", form.formCode());
        json.put("name", form.name());
        json.put("description", form.description());
        json.put("draft_status", form.draftStatus().name());
        json.put("running_version", form.runningVersion());
        json.put("bindings", form.draftBindings().stream().map(FormBinding::toJson).toList());
        json.put("created_at", form.createdAt().toString());
        json.put("updated_at", form.updatedAt().toString());
        return json;
    }

    private static Map<String, Object> versionJson(FormVersion version) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version_id", version.versionId().toString());
        json.put("form_id", version.formId().toString());
        json.put("version_no", version.versionNo());
        json.put("status", version.status().name());
        json.put("snapshot", version.snapshot().toJson());
        json.put("notes", version.notes());
        json.put("created_at", version.createdAt().toString());
        return json;
    }

    private static Map<String, Object> logJson(PublishLog log) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("log_id", log.logId().toString());
        json.put("form_id", log.formId().toString());
        json.put("version_no", log.versionNo());
        json.put("from_status", log.fromStatus());
        json.put("to_status", log.toStatus());
        json.put("outcome", log.outcome());
        json.put("message", log.message());
        json.put("details", log.details());
        json.put("trace_id", log.traceId());
        json.put("occurred_at", log.occurredAt().toString());
        return json;
    }

    private static Map<String, Object> submissionJson(FormSubmission submission) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("submission_id", submission.submissionId().toString());
        json.put("form_id", submission.formId().toString());
        json.put("running_version", submission.runningVersion());
        json.put("values", submission.values());
        json.put("submitted_at", submission.submittedAt().toString());
        json.put("submitted_by", submission.submittedBy());
        json.put("ledger_id", submission.ledgerId() == null ? null : submission.ledgerId().toString());
        json.put("ledger_record_id", submission.ledgerRecordId() == null ? null : submission.ledgerRecordId().toString());
        return json;
    }

    public record UpsertRequest(
            @NotBlank String appCode,
            @NotBlank String formCode,
            @NotBlank String name,
            String description,
            List<Map<String, Object>> bindings) {}

    public record PublishRequest(String notes) {}

    public record SubmitRequest(Map<String, Object> values) {}
}
