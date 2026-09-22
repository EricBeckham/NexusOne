package one.nexus.p1.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import one.nexus.p1.api.ApiModels.AdminInitView;
import one.nexus.p1.api.ApiModels.ConfigRequest;
import one.nexus.p1.api.ApiModels.ConfirmRequest;
import one.nexus.p1.api.ApiModels.ConsumeQuotaRequest;
import one.nexus.p1.api.ApiModels.CreateTenantRequest;
import one.nexus.p1.api.ApiModels.CreatedTenantView;
import one.nexus.p1.api.ApiModels.QuotaView;
import one.nexus.p1.api.ApiModels.TenantView;
import one.nexus.p1.application.QuotaService;
import one.nexus.p1.application.TenantConfigService;
import one.nexus.p1.application.TenantLifecycleService;
import one.nexus.p1.application.TenantLifecycleService.CreateTenantCommand;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.port.AuditRepository;

@RestController
@RequestMapping("/api/v1/p1")
public class TenantController {

    private final TenantLifecycleService lifecycle;
    private final TenantConfigService configs;
    private final QuotaService quotas;
    private final AuditRepository audit;
    private final P1Properties properties;

    public TenantController(
            TenantLifecycleService lifecycle,
            TenantConfigService configs,
            QuotaService quotas,
            AuditRepository audit,
            P1Properties properties) {
        this.lifecycle = lifecycle;
        this.configs = configs;
        this.quotas = quotas;
        this.audit = audit;
        this.properties = properties;
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedTenantView create(@Valid @RequestBody CreateTenantRequest request) {
        var created = lifecycle.create(new CreateTenantCommand(
                request.name(),
                request.code(),
                request.legalName(),
                request.admin().displayName(),
                request.admin().email(),
                request.config() == null ? null : request.config().toPatch()));
        return new CreatedTenantView(
                TenantView.of(created.tenant()),
                created.config(),
                created.projection(),
                AdminInitView.of(created.adminInit()));
    }

    @GetMapping("/tenants")
    public List<TenantView> list() {
        return lifecycle.list().stream().map(TenantView::of).toList();
    }

    @GetMapping("/tenants/{id}")
    public TenantView get(@PathVariable UUID id) {
        return TenantView.of(lifecycle.get(id));
    }

    @GetMapping("/platform-tenant")
    public TenantView platform() {
        return TenantView.of(lifecycle.get(properties.getPlatformTenant().getId()));
    }

    @PostMapping("/tenants/{id}/enable")
    public TenantView enable(@PathVariable UUID id, @RequestBody(required = false) ConfirmRequest body) {
        boolean confirm = body != null && body.confirm();
        return TenantView.of(lifecycle.enable(id, confirm));
    }

    @PostMapping("/tenants/{id}/disable")
    public TenantView disable(@PathVariable UUID id, @RequestBody(required = false) ConfirmRequest body) {
        boolean confirm = body != null && body.confirm();
        String reason = body == null ? null : body.reason();
        return TenantView.of(lifecycle.disable(id, confirm, reason));
    }

    @GetMapping("/tenants/{id}/config")
    public TenantConfig config(@PathVariable UUID id) {
        return configs.getConfig(id);
    }

    @PutMapping("/tenants/{id}/config")
    public TenantConfig updateConfig(@PathVariable UUID id, @RequestBody ConfigRequest request) {
        return configs.updateConfig(id, request.toPatch());
    }

    @GetMapping("/tenants/{id}/entitlement")
    public EntitlementProjection entitlement(@PathVariable UUID id) {
        return configs.getProjection(id);
    }

    @GetMapping("/tenants/{id}/quotas")
    public List<QuotaView> quotas(@PathVariable UUID id) {
        return quotas.list(id).stream().map(QuotaView::of).toList();
    }

    @PostMapping("/tenants/{id}/quotas/consume")
    public QuotaView consume(@PathVariable UUID id, @Valid @RequestBody ConsumeQuotaRequest request) {
        return QuotaView.of(quotas.consume(
                id, QuotaMetric.fromApi(request.metric()), request.amount(), request.idempotencyKey()));
    }

    @PostMapping("/tenants/{id}/quotas/release")
    public QuotaView release(@PathVariable UUID id, @Valid @RequestBody ConsumeQuotaRequest request) {
        return QuotaView.of(quotas.release(id, QuotaMetric.fromApi(request.metric()), request.amount()));
    }

    /**
     * Temporary read of P1-local audit staging rows. M0-11 replaces this with the platform audit API.
     */
    @GetMapping("/tenants/{id}/audit-events")
    public List<AuditEvent> auditEvents(
            @PathVariable UUID id,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(defaultValue = "50") int limit) {
        lifecycle.get(id);
        int capped = Math.min(Math.max(limit, 1), 200);
        if (eventType == null || eventType.isBlank()) {
            return audit.listByTenant(id, capped);
        }
        return audit.listByType(id, eventType, capped);
    }
}
