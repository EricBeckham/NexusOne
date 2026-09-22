package one.nexus.p3.studio.form;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable snapshot stored on a form version. */
public record FormSnapshot(
        UUID formId,
        String formCode,
        String appCode,
        String name,
        String description,
        int versionNo,
        List<FormBinding> bindings) {

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("form_id", formId.toString());
        json.put("form_code", formCode);
        json.put("app_code", appCode);
        json.put("name", name);
        json.put("description", description == null ? "" : description);
        json.put("version_no", versionNo);
        json.put("bindings", bindings.stream().map(FormBinding::toJson).toList());
        return json;
    }

    @SuppressWarnings("unchecked")
    public static FormSnapshot fromJson(Map<String, Object> json) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) json.getOrDefault("bindings", List.of());
        return new FormSnapshot(
                UUID.fromString(String.valueOf(json.get("form_id"))),
                String.valueOf(json.get("form_code")),
                String.valueOf(json.get("app_code")),
                String.valueOf(json.get("name")),
                json.get("description") == null ? "" : String.valueOf(json.get("description")),
                ((Number) json.getOrDefault("version_no", 0)).intValue(),
                raw.stream().map(FormBinding::fromJson).toList());
    }

    public static FormSnapshot of(FormRecord form, int versionNo) {
        return new FormSnapshot(
                form.formId(),
                form.formCode(),
                form.appCode(),
                form.name(),
                form.description(),
                versionNo,
                List.copyOf(form.draftBindings()));
    }
}
