package one.nexus.p2.api;

import one.nexus.p2.IdentityModule;
import one.nexus.p2.account.PersonIdentityReconciler;
import one.nexus.p2.kernel.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * G10 identity half. Platform path uses the bootstrap token; tenant path uses issued context.
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityReconcileController {
    private final IdentityModule module;

    public IdentityReconcileController(IdentityModule module) {
        this.module = module;
    }

    @GetMapping("/internal/identity/reconcile")
    public PersonIdentityReconciler.Report previewPlatform() {
        return module.personReconciler().reconcile(false);
    }

    @PostMapping("/internal/identity/reconcile")
    public PersonIdentityReconciler.Report applyPlatform(@RequestBody(required = false) ReconcileBody body) {
        boolean apply = body != null && Boolean.TRUE.equals(body.apply());
        UUID tenantId = body == null ? null : body.tenantId();
        return tenantId == null
                ? module.personReconciler().reconcile(apply)
                : module.personReconciler().reconcile(apply, tenantId);
    }

    @GetMapping("/identity/reconcile")
    public PersonIdentityReconciler.Report previewTenant() {
        return module.personReconciler().reconcile(false, TenantContextHolder.require().tenantId());
    }

    @PostMapping("/identity/reconcile")
    public PersonIdentityReconciler.Report applyTenant(@RequestBody(required = false) ReconcileBody body) {
        boolean apply = body != null && Boolean.TRUE.equals(body.apply());
        return module.personReconciler().reconcile(apply, TenantContextHolder.require().tenantId());
    }

    public record ReconcileBody(Boolean apply, UUID tenantId) {}
}
