package one.nexus.isolation.boundary;

import java.util.Collection;
import java.util.Map;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.audit.IsolationAuditorHolder;

/**
 * FieldPolicy gate on the export surface. Hidden or second-auth fields are
 * rejected before Query AST compile or file write. Mask compilation stays in
 * M0-05 {@code FieldPolicyProjector}; this guard only fail-closes the export.
 */
public final class ExportFieldPolicyGuard {
    private ExportFieldPolicyGuard() {}

    public static void assertProjection(
            AccessContext current,
            Collection<String> requestedColumns,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted
    ) {
        if (current == null) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, null, "export FieldPolicy missing context");
        }
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.FIELD_POLICY_EXPORT, current, "export projection required");
        }
        if (policy == null || policy.isEmpty()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.FIELD_POLICY_EXPORT, current, "export requires FieldPolicy");
        }
        for (String raw : requestedColumns) {
            String field = fieldName(raw);
            ExportFieldRule rule = policy.get(raw);
            if (rule == null) {
                rule = policy.get(field);
            }
            if (rule == null || !rule.visible()) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.FIELD_POLICY_EXPORT, current, "hidden or unmapped field: " + raw);
            }
            if (rule.secondAuthorization() && !secondAuthorizationGranted) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.FIELD_POLICY_EXPORT, current, "second authorization required: " + raw);
            }
        }
    }

    static String fieldName(String projection) {
        int dot = projection == null ? -1 : projection.lastIndexOf('.');
        return dot < 0 ? projection : projection.substring(dot + 1);
    }
}
