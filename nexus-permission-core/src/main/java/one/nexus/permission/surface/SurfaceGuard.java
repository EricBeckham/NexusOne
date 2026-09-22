package one.nexus.permission.surface;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.GuardRejectCapture;
import one.nexus.common.id.UuidV7;
import one.nexus.common.time.Utc;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;

/**
 * Single interpretation of {@link Decision} for list, detail, flow, form studio,
 * form fill / POST submissions, path submit-start, H5 {@code /m/*}
 * list/detail/form/todo, todo list HTTP, report query/export, physical dump /
 * raw export, catalog definition HTTP, export, Job, and Task. Surfaces
 * must not invent a second allow/deny rule: {@code !allowed} is deny.
 */
public final class SurfaceGuard {
    public static final String ENGINE = "PermissionEngine";

    private SurfaceGuard() {}

    public static Decision require(PermissionService service, String resource, String action) {
        return requireAllowed(service.decide(resource, action));
    }

    /**
     * Product wrappers reuse the {@link PermissionModule} {@link AuditPort}. Deny
     * uses {@link AuditPort#appendNewTransaction}; same-TX {@code append} is not
     * redesigned. {@code audits} may be null (tests / isolation-only).
     */
    public static Decision require(PermissionModule permission, String resource, String action) {
        if (permission == null) {
            throw PermissionException.denied("no_grant");
        }
        return require(permission.service(), permission.audits(), resource, action);
    }

    /**
     * Same deny rule as {@link #require(PermissionService, String, String)}. Rejects
     * emit {@code p2.permission.denied} and {@code isolation.guard.reject} on the
     * existing hash chain via {@code appendNewTransaction}.
     */
    public static Decision require(
            PermissionService service, AuditPort audits, String resource, String action) {
        Decision decision = service.decide(resource, action);
        if (decision == null || !decision.allowed()) {
            emitDenied(audits, resource, action, decision);
            throw PermissionException.denied(decision == null ? "no_grant" : decision.reason());
        }
        return decision;
    }

    public static Decision requireAllowed(Decision decision) {
        if (decision == null || !decision.allowed()) {
            throw PermissionException.denied(decision == null ? "no_grant" : decision.reason());
        }
        return decision;
    }

    public static void emitDenied(AuditPort audits, String resource, String action, Decision decision) {
        if (audits == null) {
            return;
        }
        TenantContext context = TenantContextHolder.get();
        String reason = decision == null ? "no_grant" : decision.reason();
        String trace = TraceIds.currentOrUnknown();
        PermissionException denied = PermissionException.denied(reason);
        UUID tenantId = context == null ? null : context.tenantId();
        UUID actorId = context == null ? null : context.personId();
        Map<String, String> details = Map.of(
                "resource", resource == null ? "" : resource,
                "action", action == null ? "" : action,
                "engine", ENGINE,
                "reason", reason);
        audits.appendNewTransaction(AuditCaptures.permission(
                false,
                tenantId,
                actorId,
                resource,
                action,
                reason,
                trace,
                details));
        audits.appendNewTransaction(AuditCaptures.guardReject(new GuardRejectCapture(
                UuidV7.generate(),
                Utc.now(),
                denied.errorCode().code(),
                actorId == null ? null : actorId.toString(),
                tenantId == null ? null : tenantId.toString(),
                resource + "." + action,
                reason,
                trace,
                details)));
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
