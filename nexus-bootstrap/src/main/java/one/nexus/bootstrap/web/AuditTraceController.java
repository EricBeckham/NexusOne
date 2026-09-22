package one.nexus.bootstrap.web;

import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.audit.AuditTrace;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hash-chain lookup by trace plus guarded mutation endpoints that never rewrite
 * the chain. Tenant scope comes from the issued {@code X-Nexus-Context-Id}.
 *
 * <p>When the caller holds a platform duty role, {@code GET} requires
 * {@code audit.read} via {@link SurfaceGuard}. WRITE / DELETE / admin-of-audit
 * always go through the same engine and 403 for audit-admin (explicit DENY).
 * Existing G4 / G18 tenant-scoped reads stay open when the caller holds no
 * platform duty.
 */
@RestController
@RequestMapping("/api/v1/audit")
@ConditionalOnBean(AuditPort.class)
public class AuditTraceController {
    private final AuditPort audits;
    private final ObjectProvider<PermissionModule> permission;

    public AuditTraceController(AuditPort audits, ObjectProvider<PermissionModule> permission) {
        this.audits = audits;
        this.permission = permission;
    }

    @GetMapping("/traces/{traceId}")
    public Map<String, Object> byTrace(@PathVariable String traceId) {
        AccessContext context = AccessContextHolder.current();
        UUID tenantId = UUID.fromString(context.boundTenantId().orElseThrow(() ->
                new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required")));
        PermissionModule module = permission.getIfAvailable();
        if (module != null && module.service().holdsPlatformDuty()) {
            SurfaceGuard.require(module.service(), audits, PlatformDutyCatalog.RESOURCE_AUDIT,
                    PlatformDutyCatalog.ACTION_READ);
        }
        return AuditTrace.jsonForTenant(traceId, tenantId, audits.findByTraceId(traceId));
    }

    @PutMapping("/config")
    public Map<String, Object> writeConfig(@RequestBody(required = false) Map<String, Object> unused) {
        return refuseMutation(PlatformDutyCatalog.RESOURCE_AUDIT_CONFIG, PlatformDutyCatalog.ACTION_WRITE);
    }

    @DeleteMapping("/traces/{traceId}")
    public Map<String, Object> deleteTrace(@PathVariable String traceId) {
        return refuseMutation(PlatformDutyCatalog.RESOURCE_AUDIT, PlatformDutyCatalog.ACTION_DELETE);
    }

    @PostMapping("/admin")
    public Map<String, Object> adminOfAudit(@RequestBody(required = false) Map<String, Object> unused) {
        return refuseMutation(PlatformDutyCatalog.RESOURCE_AUDIT, PlatformDutyCatalog.ACTION_ADMIN);
    }

    @PostMapping("/chain/mute")
    public Map<String, Object> muteChain(@RequestBody(required = false) Map<String, Object> unused) {
        return refuseMutation(PlatformDutyCatalog.RESOURCE_AUDIT_CHAIN, PlatformDutyCatalog.ACTION_MUTE);
    }

    private Map<String, Object> refuseMutation(String resource, String action) {
        PermissionModule module = permission.getIfAvailable();
        if (module != null) {
            SurfaceGuard.require(module.service(), audits, resource, action);
        } else {
            throw new NexusException(ErrorCode.API_BAD_REQUEST, 403, "permission engine is required");
        }
        throw new NexusException(ErrorCode.API_CONFLICT, 403,
                "audit hash chain is append-only; mutation is refused");
    }
}
