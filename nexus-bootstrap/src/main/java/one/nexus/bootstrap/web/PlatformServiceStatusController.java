package one.nexus.bootstrap.web;

import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/p1/platform/services")
public class PlatformServiceStatusController {
    private final IsolationGuard isolation;
    private final PlatformServiceStatusService statuses;

    public PlatformServiceStatusController(IsolationGuard isolation, PlatformServiceStatusService statuses) {
        this.isolation = isolation;
        this.statuses = statuses;
    }

    @GetMapping
    public PlatformServiceStatusService.Snapshot get() {
        isolation.requirePlatformContext("platform.service.status", PlatformDuty.SYSTEM_ADMIN);
        return statuses.snapshot();
    }
}
