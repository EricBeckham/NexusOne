package one.nexus.permission.surface;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;

/**
 * Single interpretation of {@link Decision} for list, detail, and flow.
 * Surfaces must not invent a second allow/deny rule: {@code !allowed} is deny.
 */
public final class SurfaceGuard {
    public static final String ENGINE = "PermissionEngine";

    private SurfaceGuard() {}

    public static Decision require(PermissionService service, String resource, String action) {
        return requireAllowed(service.decide(resource, action));
    }

    public static Decision requireAllowed(Decision decision) {
        if (decision == null || !decision.allowed()) {
            throw PermissionException.denied(decision == null ? "no_grant" : decision.reason());
        }
        return decision;
    }

    public static Map<String, Object> asApi(Decision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("allowed", decision.allowed());
        body.put("reason", decision.reason());
        body.put("policy_version", decision.policyVersion() == null ? 0L : decision.policyVersion());
        body.put("fields", decision.fields());
        body.put("scope_op", decision.scope() == null ? "false" : decision.scope().op());
        body.put("engine", ENGINE);
        return body;
    }
}
