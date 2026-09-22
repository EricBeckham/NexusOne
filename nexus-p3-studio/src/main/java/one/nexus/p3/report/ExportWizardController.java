package one.nexus.p3.report;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
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
 * Narrow G4 leftover: HTTP export wizard begin / complete on the existing
 * hash chain. Reuses {@link ReportSurfaceGuard} + {@link AuthorizedExport}.
 * Not a product export UI. Isolation-only hosts without the guard stay
 * tenant-only. Deny uses the existing SurfaceGuard {@code appendNewTransaction}
 * pair. Begin / complete use {@code AuditPort.append} (same-TX join).
 */
@RestController
@RequestMapping("/api/v1/p3/exports/wizard")
public class ExportWizardController {
    private final ObjectProvider<ReportSurfaceGuard> surfaces;
    private final ObjectProvider<PermissionModule> permission;
    private final ExportWizardSessions sessions;

    public ExportWizardController(
            ObjectProvider<ReportSurfaceGuard> surfaces,
            ObjectProvider<PermissionModule> permission,
            ExportWizardSessions sessions) {
        this.surfaces = surfaces;
        this.permission = permission;
        this.sessions = sessions;
    }

    @GetMapping("/decision")
    public Map<String, Object> decision() {
        ReportSurfaceGuard guard = surfaces.getIfAvailable();
        Decision export = guard == null ? ReportSurfaceGuard.isolationOnly() : guard.decideExport();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("export", SurfaceGuard.asApi(export));
        body.put("export_cell", cell(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION));
        body.put("wizard", true);
        body.put("physical_dump", false);
        return body;
    }

    @PostMapping("/begin")
    public Map<String, Object> begin(@RequestBody(required = false) WizardRequest request) {
        Decision decision = requireExport();
        PermissionModule module = requireModule();
        TenantContext context = TenantContextHolder.get();
        String tenantId = context == null ? "" : context.tenantId().toString();
        String payload = payload(request);
        ExportWizardSessions.Session session = sessions.begin(tenantId, payload);
        AuthorizedExport.beginWizard(module.service(), module.audits(), session.wizardId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wizard_id", session.wizardId());
        body.put("step", "begin");
        body.put("completed", false);
        body.put("task_type", ReportSurfaceCatalog.EXPORT_RESOURCE);
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("decision", SurfaceGuard.asApi(decision));
        body.put("physical_dump", false);
        body.put("surface", AuthorizedExport.WIZARD_SURFACE);
        return body;
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody(required = false) WizardRequest request) {
        Decision decision = requireExport();
        PermissionModule module = requireModule();
        TenantContext context = TenantContextHolder.get();
        String tenantId = context == null ? "" : context.tenantId().toString();
        String wizardId = request == null ? null : request.wizardId();
        ExportWizardSessions.Session open = sessions.requireOpen(wizardId, tenantId);
        String payload = request != null && request.payload() != null && !request.payload().isBlank()
                ? request.payload()
                : open.payload();
        if (payload == null || payload.isBlank()) {
            payload = "{\"surface\":\"export.wizard\"}";
        }
        var envelope = AuthorizedExport.completeWizard(
                module.service(), module.audits(), tenantId, payload, open.wizardId());
        sessions.complete(open.wizardId(), tenantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wizard_id", open.wizardId());
        body.put("step", "complete");
        body.put("completed", true);
        body.put("task_type", envelope.payloadType());
        body.put("engine", SurfaceGuard.ENGINE);
        body.put("decision", SurfaceGuard.asApi(decision));
        body.put("physical_dump", false);
        body.put("surface", AuthorizedExport.WIZARD_SURFACE);
        return body;
    }

    private Decision requireExport() {
        ReportSurfaceGuard guard = surfaces.getIfAvailable();
        return guard == null ? ReportSurfaceGuard.isolationOnly() : guard.requireExport();
    }

    private PermissionModule requireModule() {
        PermissionModule module = permission.getIfAvailable();
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "permission engine is required");
        }
        return module;
    }

    private static String payload(WizardRequest request) {
        if (request == null || request.payload() == null || request.payload().isBlank()) {
            return "{\"surface\":\"export.wizard\"}";
        }
        return request.payload();
    }

    private static Map<String, String> cell(String resource, String action) {
        return Map.of("resource", resource, "action", action);
    }

    public record WizardRequest(UUID ledgerId, String wizardId, String payload) {}
}
