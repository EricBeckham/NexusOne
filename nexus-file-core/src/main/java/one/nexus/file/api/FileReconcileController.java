package one.nexus.file.api;

import one.nexus.file.FileObjectReconciler;
import one.nexus.file.FileReconcileJob;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * G10 file half. Tenant path uses bound TenantContext; platform path uses
 * {@code files.reconcile} PlatformContext (bootstrap token in demo / product isolation).
 */
@RestController
@RequestMapping("/api/v1")
public class FileReconcileController {
    private final FileReconcileJob job;

    public FileReconcileController(FileReconcileJob job) {
        this.job = job;
    }

    @GetMapping("/files/reconcile")
    public FileObjectReconciler.Report previewTenant() {
        return job.run(false, requireTenant());
    }

    @PostMapping("/files/reconcile")
    public FileObjectReconciler.Report applyTenant(@RequestBody(required = false) ReconcileBody body) {
        boolean apply = body != null && Boolean.TRUE.equals(body.apply());
        return job.run(apply, requireTenant());
    }

    @GetMapping("/internal/files/reconcile")
    public FileObjectReconciler.Report previewPlatform() {
        requirePlatform();
        return job.run(false);
    }

    @PostMapping("/internal/files/reconcile")
    public FileObjectReconciler.Report applyPlatform(@RequestBody(required = false) ReconcileBody body) {
        requirePlatform();
        boolean apply = body != null && Boolean.TRUE.equals(body.apply());
        UUID tenantId = body == null ? null : body.tenantId();
        return tenantId == null ? job.run(apply) : job.run(apply, tenantId);
    }

    private static UUID requireTenant() {
        if (AccessContextHolder.current() instanceof TenantContext tenant) {
            return UUID.fromString(tenant.tenantId());
        }
        throw one.nexus.common.error.NexusException.missingTenant();
    }

    private static void requirePlatform() {
        if (!(AccessContextHolder.current() instanceof PlatformContext platform)
                || !FileObjectReconciler.PLATFORM_PATH.equals(platform.path())) {
            throw new one.nexus.common.error.NexusException(
                    one.nexus.common.error.ErrorCode.CTX_INVALID_PLATFORM,
                    403,
                    "platform path " + FileObjectReconciler.PLATFORM_PATH + " required");
        }
    }

    public record ReconcileBody(Boolean apply, UUID tenantId) {}
}
