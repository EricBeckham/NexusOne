package one.nexus.bootstrap.isolation;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.permission.engine.FieldRule;

/**
 * Maps M0-05 {@link FieldRule} onto the kernel export FieldPolicy gate.
 * Masking still happens in {@code FieldPolicyProjector}; this adapter only
 * carries visibility + second-auth into {@code IsolatedExport}.
 */
public final class ExportFieldPolicies {
    private ExportFieldPolicies() {}

    public static Map<String, ExportFieldRule> from(Map<String, FieldRule> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, ExportFieldRule> out = new LinkedHashMap<>();
        fields.forEach((name, rule) -> out.put(name, new ExportFieldRule(rule.visible(), rule.secondAuthorization())));
        return Map.copyOf(out);
    }
}
