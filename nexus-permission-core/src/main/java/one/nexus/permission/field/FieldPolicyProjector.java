package one.nexus.permission.field;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.support.PermissionException;

/**
 * Applies FieldPolicy after DataScope filtering and before the row is returned
 * to UI / API / export. Invisible fields are dropped; masks are applied;
 * editable is metadata only (writes still go through a later check).
 */
public final class FieldPolicyProjector {
    private FieldPolicyProjector() {}

    public static Map<String, Object> project(Map<String, Object> row, Map<String, FieldRule> fields) {
        return project(row, fields, false);
    }

    public static Map<String, Object> project(
            Map<String, Object> row,
            Map<String, FieldRule> fields,
            boolean secondAuthorizationGranted
    ) {
        if (row == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((name, value) -> {
            FieldRule rule = fields == null ? null : fields.get(name);
            if (rule == null) {
                out.put(name, value);
                return;
            }
            if (!rule.visible()) {
                return;
            }
            Mask mask = rule.mask();
            if (rule.secondAuthorization() && !secondAuthorizationGranted) {
                mask = Mask.strictest(mask, Mask.FULL);
            }
            out.put(name, applyMask(name, value, mask));
        });
        return out;
    }

    public static boolean canEdit(String field, Map<String, FieldRule> fields) {
        FieldRule rule = fields == null ? null : fields.get(field);
        return rule != null && rule.visible() && rule.editable();
    }

    /**
     * Write-time FieldPolicy. Empty policy means unrestricted (demo
     * {@code tenant_member} grants have no field map). A named rule that is
     * hidden or not editable rejects that field.
     */
    public static boolean canWrite(String field, Map<String, FieldRule> fields) {
        if (fields == null || fields.isEmpty()) {
            return true;
        }
        FieldRule rule = fields.get(field);
        if (rule == null) {
            return true;
        }
        return rule.visible() && rule.editable();
    }

    public static void assertWritable(Map<String, FieldRule> fields, Iterable<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (!canWrite(name, fields)) {
                throw PermissionException.denied("field_not_editable");
            }
        }
    }

    static Object applyMask(String name, Object value, Mask mask) {
        if (mask == null || mask == Mask.NONE || value == null) {
            return value;
        }
        String text = String.valueOf(value);
        if (mask == Mask.FULL) {
            return "***";
        }
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 2);
    }
}
