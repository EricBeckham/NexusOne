package one.nexus.p3.studio.form;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.p3.studio.meta.FieldRef;
import one.nexus.p3.studio.meta.LedgerMetadataPort;
import one.nexus.p3.studio.meta.LedgerRef;

/**
 * Parses widget maps into {@link FormBinding} and validates against M0-06 logical codes.
 * Physical table/column keys are rejected before metadata lookup.
 */
public final class BindingValidator {
    public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Set<String> PHYSICAL_KEYS = Set.of(
            "column",
            "column_name",
            "columnname",
            "physical_column",
            "physicalcolumn",
            "pg_column",
            "sql_column",
            "table",
            "table_name",
            "tablename",
            "physical_table",
            "physicaltable");

    private BindingValidator() {}

    public static void requireCode(String value, String field) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw new NexusException(
                    ErrorCode.FORM_INVALID, 400, "invalid " + field + " (expected [a-z][a-z0-9_]{0,62})");
        }
        if (value.startsWith("l_") || value.startsWith("a_")) {
            throw new NexusException(
                    ErrorCode.FORM_PHYSICAL_BINDING,
                    400,
                    field + " must be a logical code, not a physical l_/a_ table name",
                    Map.of("value", value));
        }
    }

    public static List<FormBinding> parse(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<FormBinding> bindings = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> row = raw.get(i) == null ? Map.of() : raw.get(i);
            rejectPhysicalKeys(row);
            String widget = string(row, "widget_code", "widgetCode");
            String ledger = string(row, "ledger_code", "ledgerCode");
            String field = string(row, "field_code", "fieldCode");
            requireCode(widget, "widget_code");
            requireCode(ledger, "ledger_code");
            requireCode(field, "field_code");
            WidgetType type = parseWidget(string(row, "widget_type", "widgetType"));
            int ordinal = number(row, "ordinal", i);
            boolean required = bool(row, "required");
            boolean readonly = bool(row, "readonly");
            bindings.add(new FormBinding(widget, ledger, field, required, readonly, type, ordinal));
        }
        return List.copyOf(bindings);
    }

    public static void validateAgainstMetadata(
            UUID tenantId, List<FormBinding> bindings, LedgerMetadataPort metadata) {
        if (bindings == null || bindings.isEmpty()) {
            throw new NexusException(ErrorCode.FORM_VALIDATE_FAILED, 400, "form needs at least one binding");
        }
        Set<String> widgets = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FormBinding binding : bindings) {
            if (!widgets.add(binding.widgetCode())) {
                errors.add(Map.of("widget_code", binding.widgetCode(), "reason", "duplicate widget_code"));
            }
            String fieldKey = binding.ledgerCode() + "." + binding.fieldCode();
            if (!fields.add(fieldKey)) {
                errors.add(Map.of("field_code", fieldKey, "reason", "duplicate ledger_code+field_code"));
            }
            LedgerRef ledger = metadata.findLedger(tenantId, binding.ledgerCode()).orElse(null);
            if (ledger == null) {
                errors.add(Map.of(
                        "ledger_code", binding.ledgerCode(),
                        "reason", "unknown ledger_code"));
                continue;
            }
            if (!ledger.usable()) {
                errors.add(Map.of(
                        "ledger_code", binding.ledgerCode(),
                        "reason", "ledger is not usable"));
            }
            FieldRef field = metadata.findField(tenantId, binding.ledgerCode(), binding.fieldCode()).orElse(null);
            if (field == null) {
                errors.add(Map.of(
                        "ledger_code", binding.ledgerCode(),
                        "field_code", binding.fieldCode(),
                        "reason", "unknown field_code"));
            }
        }
        if (!errors.isEmpty()) {
            throw new NexusException(
                    ErrorCode.FORM_VALIDATE_FAILED,
                    400,
                    "binding validation failed",
                    Map.of("errors", errors));
        }
    }

    static void rejectPhysicalKeys(Map<String, Object> row) {
        for (String key : row.keySet()) {
            if (key == null) {
                continue;
            }
            String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
            if (PHYSICAL_KEYS.contains(normalized) && row.get(key) != null && !String.valueOf(row.get(key)).isBlank()) {
                throw new NexusException(
                        ErrorCode.FORM_PHYSICAL_BINDING,
                        400,
                        "forms bind ledger_code and field_code only; physical column/table keys are forbidden",
                        Map.of("key", key));
            }
        }
    }

    private static WidgetType parseWidget(String raw) {
        if (raw == null || raw.isBlank()) {
            return WidgetType.TEXT;
        }
        try {
            return WidgetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new NexusException(ErrorCode.FORM_INVALID, 400, "unknown widget_type " + raw);
        }
    }

    private static String string(Map<String, Object> row, String snake, String camel) {
        Object value = row.containsKey(snake) ? row.get(snake) : row.get(camel);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int number(Map<String, Object> row, String key, int fallback) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
