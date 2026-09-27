package one.nexus.p4.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p3.flow.ProcessDefinitionPublishService;
import one.nexus.p4.market.MarketModels.ConfigReadinessStep;
import one.nexus.p4.market.MarketModels.ProductConfigReadiness;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ReadinessStatus;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.VersionStatus;
import one.nexus.p4.market.MarketModels.ProductDefinitionWrite;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnBean({IsolationGuard.class, AuditPort.class, ClockPort.class})
public class ProductDefinitionService {
    private final MarketRepository market;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final ObjectProvider<ProcessDefinitionPublishService> processes;
    private final UUID platformTenantId;

    public ProductDefinitionService(MarketRepository market, IsolationGuard isolation, AuditPort audit,
            ClockPort clock, ObjectProvider<ProcessDefinitionPublishService> processes, P1Properties properties) {
        this.market = market;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.processes = processes;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    @Transactional(readOnly = true)
    public List<ProductDefinition> list() {
        isolation.requirePlatformContext("product-definition.list", PlatformDuty.SYSTEM_ADMIN);
        return market.productDefinitions();
    }

    @Transactional(readOnly = true)
    public ProductDefinition get(UUID id) {
        isolation.requirePlatformContext("product-definition.get", PlatformDuty.SYSTEM_ADMIN);
        return require(id);
    }

    /**
     * PC6: wizard readiness (配表单 → 配流程 → 绑 SKU → 发布) computed from stored data only.
     * "发布" means a bound SKU belongs to an app with a PUBLISHED version, i.e. the product is in the catalog;
     * product definitions themselves have no publish action today, so their DRAFT status is reported as-is.
     */
    @Transactional(readOnly = true)
    public ProductConfigReadiness readiness(UUID id) {
        isolation.requirePlatformContext("product-definition.readiness", PlatformDuty.SYSTEM_ADMIN);
        ProductDefinition definition = require(id);
        List<Sku> skus = market.skusBoundToProductDefinition(id);
        return ProductConfigReadiness.of(definition, List.of(formStep(definition), processStep(definition),
                skuStep(skus), publishStep(skus)));
    }

    private ConfigReadinessStep formStep(ProductDefinition definition) {
        boolean published = market.publishedFormTemplateVersion(definition.formTemplateVersionId()).isPresent();
        return published
                ? new ConfigReadinessStep("form", "配表单", ReadinessStatus.DONE, "绑定的表单模板版本已发布。")
                : new ConfigReadinessStep("form", "配表单", ReadinessStatus.NOT_READY, "绑定的表单模板版本未发布或不存在。");
    }

    private ConfigReadinessStep processStep(ProductDefinition definition) {
        String key = definition.processDefinitionKey();
        Integer version = definition.processDefinitionVersion();
        if (key == null || version == null) {
            return new ConfigReadinessStep("process", "配流程", ReadinessStatus.NOT_READY, "还没有绑定流程。");
        }
        ProcessDefinitionPublishService publish = processes.getIfAvailable();
        if (publish == null) {
            return new ConfigReadinessStep("process", "配流程", ReadinessStatus.UNKNOWN, "流程服务不可用，无法确认流程是否已发布。");
        }
        try {
            return publish.isPlatformDefaultAvailable(key, version)
                    ? new ConfigReadinessStep("process", "配流程", ReadinessStatus.DONE, "流程 " + key + " v" + version + " 已发布。")
                    : new ConfigReadinessStep("process", "配流程", ReadinessStatus.NOT_READY, "绑定的流程版本未发布或不存在。");
        } catch (RuntimeException unavailable) {
            return new ConfigReadinessStep("process", "配流程", ReadinessStatus.UNKNOWN, "暂时无法确认流程是否已发布。");
        }
    }

    private static ConfigReadinessStep skuStep(List<Sku> skus) {
        if (skus.isEmpty()) return new ConfigReadinessStep("sku", "绑 SKU", ReadinessStatus.NOT_READY, "还没有绑定 SKU。");
        return new ConfigReadinessStep("sku", "绑 SKU", ReadinessStatus.DONE,
                "已绑定 " + String.join("、", skus.stream().map(Sku::code).toList()) + "。");
    }

    private ConfigReadinessStep publishStep(List<Sku> skus) {
        if (skus.isEmpty()) {
            return new ConfigReadinessStep("publish", "发布", ReadinessStatus.NOT_READY, "绑定 SKU 后，产品随应用的已发布版本在星市上架。");
        }
        boolean listed = skus.stream().map(Sku::appId).distinct()
                .anyMatch(appId -> market.versions(appId).stream().anyMatch(v -> v.status() == VersionStatus.PUBLISHED));
        return listed
                ? new ConfigReadinessStep("publish", "发布", ReadinessStatus.DONE, "绑定的 SKU 所属应用有已发布版本，产品已在星市上架。")
                : new ConfigReadinessStep("publish", "发布", ReadinessStatus.NOT_READY, "绑定的 SKU 所属应用还没有已发布版本。");
    }

    @Transactional
    public ProductDefinition create(ProductDefinitionWrite request) {
        PlatformContext actor = isolation.requirePlatformContext("product-definition.create", PlatformDuty.SYSTEM_ADMIN);
        validate(request);
        Instant now = clock.now();
        ProductDefinition definition = new ProductDefinition(UUID.randomUUID(), request.code().trim(), request.name().trim(),
                request.description(), request.formTemplateVersionId(), blankToNull(request.processDefinitionKey()),
                request.processDefinitionVersion(), request.defaultPermissionPack(), "DRAFT", now, now, 1);
        market.saveProductDefinition(definition);
        append(actor, "created", definition);
        return definition;
    }

    @Transactional
    public ProductDefinition update(UUID id, ProductDefinitionWrite request) {
        PlatformContext actor = isolation.requirePlatformContext("product-definition.update", PlatformDuty.SYSTEM_ADMIN);
        validate(request);
        ProductDefinition current = require(id);
        if (!"DRAFT".equals(current.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "only draft product definitions can be updated");
        ProductDefinition next = new ProductDefinition(id, request.code().trim(), request.name().trim(), request.description(),
                request.formTemplateVersionId(), blankToNull(request.processDefinitionKey()), request.processDefinitionVersion(),
                request.defaultPermissionPack(), current.status(), current.createdAt(), clock.now(), current.version() + 1);
        market.saveProductDefinition(next);
        append(actor, "updated", next);
        return next;
    }

    @Transactional
    public ProductDefinition bindSku(String skuCode, UUID productDefinitionId) {
        PlatformContext actor = isolation.requirePlatformContext("product-definition.bind-sku", PlatformDuty.SYSTEM_ADMIN);
        if (market.sku(skuCode).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found");
        ProductDefinition definition = require(productDefinitionId);
        market.bindSkuToProductDefinition(skuCode, productDefinitionId);
        Instant now = clock.now();
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.product_definition.sku_bound", AuditEvent.VERSION,
                now, actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED, "sku", skuCode,
                Map.of("product_definition_id", productDefinitionId.toString())));
        return definition;
    }

    private void validate(ProductDefinitionWrite request) {
        if (request == null || blank(request.code()) || request.code().length() > 80 || blank(request.name())
                || request.name().length() > 160 || request.formTemplateVersionId() == null
                || request.defaultPermissionPack() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code, name, form template version, and permission pack are required");
        }
        if (market.publishedFormTemplateVersion(request.formTemplateVersionId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "form template version is not published or does not exist");
        }
        String key = blankToNull(request.processDefinitionKey());
        if ((key == null) != (request.processDefinitionVersion() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "process key and version must be supplied together");
        }
        if (key != null && (request.processDefinitionVersion() <= 0 || processes.getIfAvailable() == null
                || !processes.getIfAvailable().isPlatformDefaultAvailable(key, request.processDefinitionVersion()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "process definition version is not published or does not exist");
        }
        if (request.defaultPermissionPack().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "permission pack is too large");
        }
    }

    private ProductDefinition require(UUID id) {
        return market.productDefinition(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));
    }

    private void append(PlatformContext actor, String action, ProductDefinition definition) {
        Instant now = clock.now();
        // platform.audit_event.tenant_id is NOT NULL: prefer actor home tenant, else configured platform tenant.
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.product_definition." + action, AuditEvent.VERSION,
                now, actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                "product_definition", definition.id().toString(),
                Map.of("code", definition.code(), "version", definition.version())));
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static String blankToNull(String text) { return blank(text) ? null : text.trim(); }
}
