package one.nexus.permission.surface;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;

/**
 * FieldPolicy projection for physical dump / raw export. Hidden cells are
 * dropped before {@link AuthorizedExport#writeProjected}. Unmapped columns
 * stay visible so an empty export-cell field map still has a policy.
 */
public final class DumpFieldPolicies {
    public static final Set<String> SYSTEM_SKIP =
            Set.of("tenant_id", "deleted_at", "ext_data", "schema_version", "_source");

    private DumpFieldPolicies() {}

    public static List<String> visibleColumns(Decision read, List<Map<String, Object>> rows) {
        Set<String> keys = new TreeSet<>();
        Map<String, FieldRule> fields = read == null ? Map.of() : read.fields();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                for (String key : row.keySet()) {
                    if (!SYSTEM_SKIP.contains(key) && visible(fields, key)) {
                        keys.add(key);
                    }
                }
            }
        }
        if (keys.isEmpty() && visible(fields, "id")) {
            keys.add("id");
        }
        if (keys.isEmpty()) {
            keys.add("id");
        }
        return List.copyOf(keys);
    }

    public static Map<String, ExportFieldRule> policyFor(Decision read, List<String> columns) {
        Map<String, ExportFieldRule> policy = new LinkedHashMap<>();
        Map<String, FieldRule> fields = read == null ? Map.of() : read.fields();
        for (String column : columns) {
            FieldRule rule = fields.get(column);
            if (rule == null) {
                policy.put(column, ExportFieldRule.allow());
            } else {
                policy.put(column, new ExportFieldRule(rule.visible(), rule.secondAuthorization()));
            }
        }
        return Map.copyOf(policy);
    }

    static boolean visible(Map<String, FieldRule> fields, String key) {
        FieldRule rule = fields.get(key);
        return rule == null || rule.visible();
    }
}
