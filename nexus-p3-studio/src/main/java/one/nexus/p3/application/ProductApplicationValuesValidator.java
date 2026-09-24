package one.nexus.p3.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Pure field-level validator for application values against an immutable published form snapshot. */
public final class ProductApplicationValuesValidator {
    private ProductApplicationValuesValidator() { }

    public static List<FieldError> validate(UUID tenantId, List<Field> fields, JsonNode values,
            TenantValueOwnership ownership) {
        List<FieldError> errors = new ArrayList<>();
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (fields == null || fields.isEmpty()) return List.of(new FieldError(null,"template.fields.required","Template has no published fields"));
        if (values == null || !values.isObject()) return List.of(new FieldError(null,"values.object.required","Values must be a JSON object"));

        Set<String> knownKeys = new HashSet<>();
        for (Field field : fields) knownKeys.add(field.key());
        for (String key : values.propertyNames()) {
            if (!knownKeys.contains(key)) errors.add(new FieldError(key,"field.unknown","Field is not in the pinned template"));
        }

        for (Field field : fields) {
            JsonNode value = values.get(field.key());
            if (isMissing(value)) {
                if (field.required()) errors.add(new FieldError(field.key(),"field.required","A value is required"));
                continue;
            }
            if (!valid(field, value, tenantId, ownership)) {
                errors.add(new FieldError(field.key(),"field.value.invalid",message(field.type())));
            }
        }
        return List.copyOf(errors);
    }

    private static boolean valid(Field field, JsonNode value, UUID tenantId, TenantValueOwnership ownership) {
        return switch (field.type()) {
            case "TEXT", "TEXTAREA" -> value.isTextual();
            case "NUMBER", "MONEY" -> value.isNumber();
            case "DATE" -> value.isTextual() && validDate(value.asText());
            case "SELECT" -> value.isTextual() && field.options().contains(value.asText());
            case "PERSON" -> value.isTextual() && validUuid(value.asText(), id -> ownership.personBelongsToTenant(tenantId,id));
            case "ATTACHMENT" -> validAttachments(value,tenantId,ownership);
            default -> false;
        };
    }

    private static boolean validAttachments(JsonNode value, UUID tenantId, TenantValueOwnership ownership) {
        if (value.isTextual()) return validUuid(value.asText(), id -> ownership.fileBelongsToTenant(tenantId,id));
        if (!value.isArray() || value.isEmpty()) return false;
        for (JsonNode item : value) {
            if (!item.isTextual() || !validUuid(item.asText(), id -> ownership.fileBelongsToTenant(tenantId,id))) return false;
        }
        return true;
    }

    private static boolean validUuid(String value, java.util.function.Predicate<UUID> check) {
        try { return check.test(UUID.fromString(value)); }
        catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean validDate(String value) {
        try { LocalDate.parse(value); return true; }
        catch (DateTimeParseException ex) { return false; }
    }

    private static boolean isMissing(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                || (value.isArray() && value.isEmpty());
    }

    private static String message(String type) {
        return switch (type) {
            case "SELECT" -> "Select a configured option";
            case "PERSON" -> "Select an active person in this tenant";
            case "ATTACHMENT" -> "Select uploaded file(s) belonging to this tenant";
            case "DATE" -> "Enter a valid ISO date";
            case "NUMBER", "MONEY" -> "Enter a numeric value";
            case "TEXT", "TEXTAREA" -> "Enter text";
            default -> "Unsupported template field type";
        };
    }

    public record Field(String key, String type, boolean required, Set<String> options) {
        public Field { options = options == null ? Set.of() : Set.copyOf(options); }
    }
    public record FieldError(String fieldKey, String code, String message) { }
    public interface TenantValueOwnership {
        boolean personBelongsToTenant(UUID tenantId, UUID personId);
        boolean fileBelongsToTenant(UUID tenantId, UUID fileId);
    }
}
