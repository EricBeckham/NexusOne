package one.nexus.bootstrap.export;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin export entry. Isolation is the existing {@code IssuedTenantIsolationFilter}
 * on {@code /api/v1/p3/*}. CSV body is the ledger code only — not a FieldPolicy
 * dump of physical {@code l_*} rows.
 */
@RestController
@RequestMapping("/api/v1/p3/exports")
@ConditionalOnBean(AuditedExport.class)
public class ExportController {
    private static final Pattern LEDGER_CODE = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private final AuditedExport exports;

    public ExportController(AuditedExport exports) {
        this.exports = exports;
    }

    @PostMapping
    public Map<String, Object> export(@RequestBody(required = false) ExportRequest request) {
        AccessContext context = AccessContextHolder.current();
        String tenantId = context.boundTenantId().orElseThrow(() ->
                new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required"));
        String ledgerCode = request == null || request.ledgerCode() == null || request.ledgerCode().isBlank()
                ? "ledger"
                : request.ledgerCode();
        if (!LEDGER_CODE.matcher(ledgerCode).matches()) {
            throw new NexusException(ErrorCode.API_BAD_REQUEST, 400, "ledger_code must be [A-Za-z0-9_]{1,64}");
        }
        String payload = "{\"ledger_code\":\"" + ledgerCode + "\",\"format\":\"csv\"}";
        IsolatedEnvelope envelope = exports.submit(tenantId, payload);
        String relative = "export/" + ledgerCode + ".csv";
        byte[] csv = (ledgerCode + "\n").getBytes(StandardCharsets.UTF_8);
        String objectKey = exports.writeFile(tenantId, relative, csv);
        exports.dispatch(envelope, tenantId, item -> { });
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("trace_id", envelope.traceId());
        json.put("tenant_id", tenantId);
        json.put("task_type", IsolatedExport.TASK_TYPE);
        json.put("ledger_code", ledgerCode);
        json.put("format", "csv");
        json.put("object_key", objectKey);
        json.put("bytes", csv.length);
        return json;
    }

    public record ExportRequest(String ledgerCode) {}
}
