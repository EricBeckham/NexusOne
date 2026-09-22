package one.nexus.p3.studio.form;

import java.util.LinkedHashMap;
import java.util.Map;

/** One widget bound to M0-06 logical codes. No physical column. */
public record FormBinding(
        String widgetCode,
        String ledgerCode,
        String fieldCode,
        boolean required,
        boolean readonly,
        WidgetType widgetType,
        int ordinal) {

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("widget_code", widgetCode);
        json.put("ledger_code", ledgerCode);
        json.put("field_code", fieldCode);
        json.put("required", required);
        json.put("readonly", readonly);
        json.put("widget_type", widgetType.name());
        json.put("ordinal", ordinal);
        return json;
    }

    @SuppressWarnings("unchecked")
    public static FormBinding fromJson(Map<String, Object> json) {
        return new FormBinding(
                string(json, "widget_code"),
                string(json, "ledger_code"),
                string(json, "field_code"),
                bool(json, "required"),
                bool(json, "readonly"),
                WidgetType.valueOf(string(json, "widget_type")),
                ((Number) json.getOrDefault("ordinal", 0)).intValue());
    }

    private static String string(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
