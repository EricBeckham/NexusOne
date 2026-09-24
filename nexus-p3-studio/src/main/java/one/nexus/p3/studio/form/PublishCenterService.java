package one.nexus.p3.studio.form;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.path.BusinessTypeCatalog;

/** Read-only publish-center projection over existing forms, versions, and publish logs. */
public final class PublishCenterService {
    public static final String SCHEMA_NOTICE =
            "回滚不会回滚共享物理 Schema，也不会删除已有业务数据。";

    private final FormService forms;

    public PublishCenterService(FormService forms) {
        this.forms = forms;
    }

    public List<Map<String, Object>> list() {
        return forms.list().stream().map(form -> summary(form, forms.publishLog(form.formId()))).toList();
    }

    public Map<String, Object> detail(UUID formId) {
        FormRecord form = forms.get(formId);
        List<PublishLog> logs = forms.publishLog(formId);
        Map<String, Object> json = summary(form, logs);
        json.put("versions", forms.versions(formId).stream().map(PublishCenterService::version).toList());
        json.put("logs", logs.stream().map(PublishCenterService::log).toList());
        json.put("schema_steps", List.of("Expand", "Migrate", "Switch", "Contract"));
        json.put("schema_execution", false);
        json.put("schema_notice", SCHEMA_NOTICE);
        return json;
    }

    private static Map<String, Object> summary(FormRecord form, List<PublishLog> logs) {
        PublishLog latest = logs.stream().max(Comparator.comparing(PublishLog::occurredAt)).orElse(null);
        String raw = rawStatus(form, latest);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", form.formId().toString());
        json.put("form_id", form.formId().toString());
        json.put("form_code", form.formCode());
        json.put("name", form.name());
        json.put("app_code", form.appCode());
        json.put("status", PublishCenterStatus.of(form, latest));
        json.put("source_status", raw);
        json.put("running_version", form.runningVersion());
        json.put("latest_log", latest == null ? null : log(latest));
        BusinessTypeCatalog.findByFormCode(form.formCode()).ifPresent(type -> {
            Map<String, Object> provision = new LinkedHashMap<>();
            provision.put("type_code", type.code());
            provision.put("display_name", type.displayName());
            provision.put("class_name", type.className());
            json.put("provision", provision);
        });
        return json;
    }

    private static String rawStatus(FormRecord form, PublishLog latest) {
        if (latest != null && "ROLLED_BACK".equals(latest.outcome())) {
            return latest.outcome();
        }
        if (form.runningVersion() != null) {
            return VersionStatus.RUNNING.name();
        }
        if (latest != null && latest.toStatus() != null && !latest.toStatus().isBlank()) {
            return latest.toStatus();
        }
        return form.draftStatus() == null ? null : form.draftStatus().name();
    }

    private static Map<String, Object> version(FormVersion version) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version_no", version.versionNo());
        json.put("status", PublishCenterStatus.map(version.status().name()));
        json.put("source_status", version.status().name());
        json.put("notes", version.notes());
        json.put("created_at", version.createdAt().toString());
        return json;
    }

    private static Map<String, Object> log(PublishLog row) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("log_id", row.logId().toString());
        json.put("version_no", row.versionNo());
        json.put("from_status", row.fromStatus());
        json.put("to_status", row.toStatus());
        json.put("outcome", row.outcome());
        json.put("message", row.message());
        json.put("occurred_at", row.occurredAt().toString());
        return json;
    }
}
