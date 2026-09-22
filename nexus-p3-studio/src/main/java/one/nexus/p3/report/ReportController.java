package one.nexus.p3.report;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.application.LedgerRecordService;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore.Page;
import one.nexus.p3.infra.permission.ReportSurfaceGuard;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.AuthorizedExport;
import one.nexus.permission.surface.ReportSurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Report query / export HTTP. Query is {@code ledger}/{@code read}; export is
 * {@code ledger.export.csv}/{@code submit} through {@link ReportSurfaceGuard}
 * then {@link AuthorizedExport}. Not a physical dump. Isolation-only hosts
 * without the guard stay tenant-only.
 */
@RestController
@RequestMapping("/api/v1/p3/reports")
public class ReportController {
    private final ObjectProvider<ReportSurfaceGuard> surfaces;
    private final ObjectProvider<LedgerRecordService> records;
    private final ObjectProvider<PermissionModule> permission;

    public ReportController(
            ObjectProvider<ReportSurfaceGuard> surfaces,
            ObjectProvider<LedgerRecordService> records,
            ObjectProvider<PermissionModule> permission) {
        this.surfaces = surfaces;
        this.records = records;
        this.permission = permission;
    }

    @GetMapping("/decision")
    public Map<String, Object> decision() {
        ReportSurfaceGuard guard = surfaces.getIfAvailable();
        Decision query = guard == null ? ReportSurfaceGuard.isolationOnly() : guard.decideQuery();
        Decision export = guard == null ? ReportSurfaceGuard.isolationOnly() : guard.decideExport();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("query", SurfaceGuard.asApi(query));
        body.put("export", SurfaceGuard.asApi(export));
        body.put("query_cell", cell(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION));
        body.put("export_cell", cell(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION));
        body.put("physical_dump", "open");
        return body;
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody(required = false) QueryRequest request) {
        Decision decision = requireQuery();
        UUID ledgerId = request == null ? null : request.ledgerId();
        if (ledgerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ledger_id is required");
        }
        LedgerRecordService store = records.getIfAvailable();
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ledger record service is required");
        }
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        Page result = store.list(ledgerId, filters, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.items());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("decision", SurfaceGuard.asApi(decision));
        return body;
    }

    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody(required = false) ExportRequest request) {
        Decision decision = requireExport();
        PermissionModule module = permission.getIfAvailable();
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "permission engine is required");
        }
        TenantContext context = TenantContextHolder.get();
        String tenantId = context == null ? "" : context.tenantId().toString();
        String payload = request == null || request.payload() == null || request.payload().isBlank()
                ? "{\"surface\":\"report\"}"
                : request.payload();
        var envelope = AuthorizedExport.submit(module.service(), module.audits(), tenantId, payload);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_type", envelope.payloadType());
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("decision", SurfaceGuard.asApi(decision));
        body.put("physical_dump", false);
        return body;
    }

    private Decision requireQuery() {
        ReportSurfaceGuard guard = surfaces.getIfAvailable();
        return guard == null ? ReportSurfaceGuard.isolationOnly() : guard.requireQuery();
    }

    private Decision requireExport() {
        ReportSurfaceGuard guard = surfaces.getIfAvailable();
        return guard == null ? ReportSurfaceGuard.isolationOnly() : guard.requireExport();
    }

    private static Map<String, String> cell(String resource, String action) {
        return Map.of("resource", resource, "action", action);
    }

    public record QueryRequest(UUID ledgerId, Map<String, String> filters, Integer page, Integer size) {}

    public record ExportRequest(UUID ledgerId, String payload) {}
}
