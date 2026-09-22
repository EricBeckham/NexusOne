package one.nexus.bootstrap.web;

import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.audit.AuditTrace;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only hash-chain lookup by trace. Tenant scope comes from the issued
 * {@code X-Nexus-Context-Id} via {@code IssuedTenantIsolationFilter}.
 */
@RestController
@RequestMapping("/api/v1/audit")
@ConditionalOnBean(AuditPort.class)
public class AuditTraceController {
    private final AuditPort audits;

    public AuditTraceController(AuditPort audits) {
        this.audits = audits;
    }

    @GetMapping("/traces/{traceId}")
    public Map<String, Object> byTrace(@PathVariable String traceId) {
        AccessContext context = AccessContextHolder.current();
        UUID tenantId = UUID.fromString(context.boundTenantId().orElseThrow(() ->
                new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required")));
        return AuditTrace.jsonForTenant(traceId, tenantId, audits.findByTraceId(traceId));
    }
}
