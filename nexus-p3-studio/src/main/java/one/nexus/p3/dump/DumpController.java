package one.nexus.p3.dump;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.application.LedgerRecordService;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore.Page;
import one.nexus.p3.infra.permission.DumpSurfaceGuard;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.AuthorizedExport;
import one.nexus.permission.surface.DumpFieldPolicies;
import one.nexus.permission.surface.DumpSurfaceCatalog;
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
 * Physical ledger dump / raw export HTTP. Read is {@code ledger}/{@code read};
 * dump is {@code ledger.export.csv}/{@code submit} through
 * {@link DumpSurfaceGuard} then {@link AuthorizedExport#writeProjected}.
 * Not report Task submit. Isolation-only hosts without the guard stay
 * tenant-only. {@code pg_dump} ops stay off this engine.
 */
@RestController
@RequestMapping("/api/v1/p3/dumps")
public class DumpController {
    private final ObjectProvider<DumpSurfaceGuard> surfaces;
    private final ObjectProvider<LedgerRecordService> records;
    private final ObjectProvider<PermissionModule> permission;

    public DumpController(
            ObjectProvider<DumpSurfaceGuard> surfaces,
            ObjectProvider<LedgerRecordService> records,
            ObjectProvider<PermissionModule> permission) {
        this.surfaces = surfaces;
        this.records = records;
        this.permission = permission;
    }

    @GetMapping("/decision")
    public Map<String, Object> decision() {
        DumpSurfaceGuard guard = surfaces.getIfAvailable();
        Decision read = guard == null ? DumpSurfaceGuard.isolationOnly() : guard.decideRead();
        Decision dump = guard == null ? DumpSurfaceGuard.isolationOnly() : guard.decideDump();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("read", SurfaceGuard.asApi(read));
        body.put("dump", SurfaceGuard.asApi(dump));
        body.put("read_cell", cell(DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION));
        body.put("dump_cell", cell(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION));
        body.put("physical_dump", true);
        body.put("raw_export", true);
        return body;
    }

    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody(required = false) DumpRequest request) {
        Decision dump = requireDump();
        Decision read = requireRead();
        UUID ledgerId = request == null ? null : request.ledgerId();
        if (ledgerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ledger_id is required");
        }
        LedgerRecordService store = records.getIfAvailable();
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ledger record service is required");
        }
        PermissionModule module = permission.getIfAvailable();
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "permission engine is required");
        }
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : Math.min(request.size(), 1000);
        Page result = store.list(ledgerId, filters, page, size);
        List<Map<String, Object>> rows = result.items();
        List<String> columns = DumpFieldPolicies.visibleColumns(read, rows);
        Map<String, ExportFieldRule> policy = DumpFieldPolicies.policyFor(read, columns);
        TenantContext context = TenantContextHolder.get();
        String tenantId = context == null ? "" : context.tenantId().toString();
        AtomicReference<String> fileKey = new AtomicReference<>();
        AtomicReference<byte[]> csv = new AtomicReference<>();
        AuthorizedExport.writeProjected(
                module.service(),
                tenantId,
                "export/dump/" + ledgerId + ".csv",
                columns,
                rows,
                policy,
                false,
                (key, bodyBytes) -> {
                    fileKey.set(key);
                    csv.set(bodyBytes);
                });
        byte[] bytes = csv.get() == null ? new byte[0] : csv.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("decision", SurfaceGuard.asApi(dump));
        body.put("read_decision", SurfaceGuard.asApi(read));
        body.put("physical_dump", true);
        body.put("raw_export", true);
        body.put("task_type", DumpSurfaceCatalog.DUMP_RESOURCE);
        body.put("file_key", fileKey.get() == null ? "" : fileKey.get());
        body.put("columns", columns);
        body.put("row_count", result.total());
        body.put("csv", new String(bytes, StandardCharsets.UTF_8));
        return body;
    }

    private Decision requireRead() {
        DumpSurfaceGuard guard = surfaces.getIfAvailable();
        return guard == null ? DumpSurfaceGuard.isolationOnly() : guard.requireRead();
    }

    private Decision requireDump() {
        DumpSurfaceGuard guard = surfaces.getIfAvailable();
        return guard == null ? DumpSurfaceGuard.isolationOnly() : guard.requireDump();
    }

    private static Map<String, String> cell(String resource, String action) {
        return Map.of("resource", resource, "action", action);
    }

    public record DumpRequest(UUID ledgerId, Map<String, String> filters, Integer page, Integer size) {}
}
