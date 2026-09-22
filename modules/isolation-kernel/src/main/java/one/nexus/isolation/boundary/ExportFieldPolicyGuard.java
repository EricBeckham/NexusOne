package one.nexus.isolation.boundary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.audit.IsolationAuditorHolder;

/**
 * FieldPolicy gate on the export surface. Hidden columns on a Query AST
 * projection are dropped (same visibility encoding as
 * {@code QueryAstPolicyCompiler}) instead of rejected. Unmapped fields,
 * empty leftover projection, empty policy, and second-auth without grant
 * still fail-close as {@code P1.M02.021}. Mask compilation stays in M0-05
 * {@code FieldPolicyProjector}.
 */
public final class ExportFieldPolicyGuard {
    private ExportFieldPolicyGuard() {}

    public record Projection(List<String> visible, List<String> dropped) {
        public Projection {
            visible = List.copyOf(visible == null ? List.of() : visible);
            dropped = List.copyOf(dropped == null ? List.of() : dropped);
        }
    }

    public static void assertProjection(
            AccessContext current,
            Collection<String> requestedColumns,
            Map<String, ExportFieldRule> policy,
            boolean secondAuthorizationGranted
    ) {
        dropHidden(current, requestedColumns, policy, secondAuthorizationGranted);
    }

    public static Projection dropHidden(
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
        List<String> visible = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (String raw : requestedColumns) {
            String field = fieldName(raw);
            ExportFieldRule rule = policy.get(raw);
            if (rule == null) {
                rule = policy.get(field);
            }
            if (rule == null) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.FIELD_POLICY_EXPORT, current, "unmapped field: " + raw);
            }
            if (!rule.visible()) {
                dropped.add(raw);
                continue;
            }
            if (rule.secondAuthorization() && !secondAuthorizationGranted) {
                throw IsolationAuditorHolder.reject(
                        IsolationErrorCode.FIELD_POLICY_EXPORT, current, "second authorization required: " + raw);
            }
            visible.add(raw);
        }
        if (visible.isEmpty()) {
            throw IsolationAuditorHolder.reject(
                    IsolationErrorCode.FIELD_POLICY_EXPORT, current, "FieldPolicy left an empty projection");
        }
        return new Projection(visible, dropped);
    }

    static String fieldName(String projection) {
        int dot = projection == null ? -1 : projection.lastIndexOf('.');
        return dot < 0 ? projection : projection.substring(dot + 1);
    }
}
