package one.nexus.p3.portal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLC3. Tenant comes from the server context. */
@RestController
@RequestMapping("/api/v1/p3/portal")
public class PortalConfigController {
    private final PortalConfigService config;

    public PortalConfigController(PortalConfigService config) {
        this.config = config;
    }

    @GetMapping("/menu")
    public MenuBody menu() {
        return new MenuBody(config.menu(requireTenant()));
    }

    @PutMapping("/menu")
    public MenuBody replaceMenu(@RequestBody MenuBody body) {
        return new MenuBody(config.replaceMenu(requireTenant(), body == null ? List.of() : body.items()));
    }

    @GetMapping("/home-modules")
    public ModulesBody modules() {
        return new ModulesBody(config.modules(requireTenant()));
    }

    @PutMapping("/home-modules")
    public ModulesBody replaceModules(@RequestBody ModulesBody body) {
        return new ModulesBody(config.replaceModules(requireTenant(), body == null ? List.of() : body.modules()));
    }

    private static UUID requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "TenantContext is required", Map.of());
        }
        return context.tenantId();
    }

    public record MenuBody(List<MenuItem> items) {}

    public record ModulesBody(List<HomeModule> modules) {}
}
