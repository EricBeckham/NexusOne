package one.nexus.p4.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import one.nexus.p4.market.MarketModels.FormFieldSnapshot;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.Sku;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** IM4: assemble product-config format 1.0 JSON bundle from existing tables (no Flyway). */
@Service
@ConditionalOnBean({IsolationGuard.class, AuditPort.class, ClockPort.class})
public class ProductConfigExportService {
    private final MarketRepository market;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final ObjectProvider<ProcessDefinitionPublishService> processes;
    private final UUID platformTenantId;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public ProductConfigExportService(
            MarketRepository market,
            IsolationGuard isolation,
            AuditPort audit,
            ClockPort clock,
            ObjectProvider<ProcessDefinitionPublishService> processes,
            P1Properties properties) {
        this.market = market;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.processes = processes;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportBundle(UUID definitionId) {
        PlatformContext actor = isolation.requirePlatformContext("product-definition.export", PlatformDuty.SYSTEM_ADMIN);
        ProductDefinition definition = market.productDefinition(definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));

        Map<String, Object> packageBody = new LinkedHashMap<>();
        packageBody.put("manifest", manifestWithoutChecksum(definition, clock.now()));
        packageBody.put("formTemplates", List.of(formTemplateRow(definition)));
        packageBody.put("processDefinitions", List.of(processRow(definition)));
        packageBody.put("productDefinition", productRow(definition));
        packageBody.put("skuBindings", skuRows(definitionId));
        packageBody.put("printTemplates", List.of());
        packageBody.put("printSupport", "config+preview-later");

        String checksum = "sha256:" + sha256Hex(writeCanonical(packageBody));
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) packageBody.get("manifest");
        manifest.put("checksum", checksum);

        Instant now = clock.now();
        audit.append(new AuditEvent(
                UUID.randomUUID(),
                "platform.product_definition.exported",
                AuditEvent.VERSION,
                now,
                actor.auditTenantId(platformTenantId),
                actor.actor().subjectId(),
                actor.actorHomeTenantId(),
                true,
                actor.reason(),
                actor.traceId(),
                actor.traceId(),
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "product_definition",
                definitionId.toString(),
                Map.of("code", definition.code(), "checksum", checksum)));
        return packageBody;
    }

    public byte[] toJsonBytes(Map<String, Object> bundle) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize product-config bundle", ex);
        }
    }

    public String suggestedFilename(Map<String, Object> bundle) {
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) bundle.get("productDefinition");
        Object code = product == null ? null : product.get("code");
        String safe = code == null ? "product" : String.valueOf(code).replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe + ".product-config.json";
    }

    private Map<String, Object> manifestWithoutChecksum(ProductDefinition definition, Instant exportedAt) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("env", "platform");
        source.put("productDefinitionId", definition.id().toString());
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "nexusone.product-config");
        manifest.put("formatVersion", "1.0");
        manifest.put("exportedAt", exportedAt.toString());
        manifest.put("source", source);
        return manifest;
    }

    private Map<String, Object> formTemplateRow(ProductDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        FormTemplateVersionSnapshot snap = market.formTemplateVersionSnapshot(definition.formTemplateVersionId())
                .orElse(null);
        if (snap == null) {
            row.put("id", definition.formTemplateVersionId() == null ? null : definition.formTemplateVersionId().toString());
            row.put("code", null);
            row.put("name", null);
            row.put("version", null);
            row.put("status", "MISSING");
            row.put("fields", List.of());
            row.put("note", "绑定的表单模板版本不存在或无法读取快照");
            return row;
        }
        row.put("id", snap.versionId().toString());
        row.put("code", snap.templateCode());
        row.put("name", snap.templateName());
        row.put("version", snap.version());
        row.put("status", market.publishedFormTemplateVersion(snap.versionId()).isPresent() ? "PUBLISHED" : "DRAFT");
        List<Map<String, Object>> fields = new ArrayList<>();
        for (FormFieldSnapshot field : snap.fields()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("code", field.key());
            f.put("type", field.type() == null ? "TEXT" : field.type().toLowerCase());
            f.put("label", field.label());
            f.put("required", field.required());
            fields.add(f);
        }
        row.put("fields", fields);
        return row;
    }

    private Map<String, Object> processRow(ProductDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        String key = definition.processDefinitionKey();
        Integer version = definition.processDefinitionVersion();
        row.put("key", key);
        row.put("version", version);
        if (key == null || version == null) {
            row.put("name", null);
            row.put("nodes", List.of());
            row.put("edges", List.of());
            row.put("note", "产品尚未绑定流程");
            return row;
        }
        ProcessDefinitionPublishService publish = processes.getIfAvailable();
        if (publish != null) {
            var catalog = publish.catalogForVersion(key, version);
            if (catalog.isPresent()) {
                Map<String, Object> snap = catalog.get();
                row.put("name", snap.getOrDefault("name", key));
                row.put("nodes", snap.getOrDefault("nodes", List.of()));
                row.put("edges", edgesFromCatalog(snap));
                return row;
            }
        }
        row.put("name", key);
        row.put("nodes", List.of());
        row.put("edges", List.of());
        row.put("note", "流程元数据已导出；BPMN 图暂不可用");
        return row;
    }

    private Map<String, Object> productRow(ProductDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", definition.id().toString());
        row.put("code", definition.code());
        row.put("name", definition.name());
        row.put("description", definition.description());
        row.put("status", definition.status());
        FormTemplateVersionSnapshot snap = market.formTemplateVersionSnapshot(definition.formTemplateVersionId())
                .orElse(null);
        row.put("formTemplateCode", snap == null ? null : snap.templateCode());
        row.put("formTemplateVersion", snap == null ? null : snap.version());
        row.put("processDefinitionKey", definition.processDefinitionKey());
        row.put("processDefinitionVersion", definition.processDefinitionVersion());
        return row;
    }

    private List<Map<String, Object>> skuRows(UUID definitionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Sku sku : market.skusBoundToProductDefinition(definitionId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuCode", sku.code());
            row.put("skuName", sku.name());
            row.put("entitlementKeys", sku.entitlements() == null ? List.of() : List.copyOf(sku.entitlements().keySet()));
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgesFromCatalog(Map<String, Object> snap) {
        Object edges = snap.get("edges");
        if (edges instanceof List<?> list && !list.isEmpty()) {
            return (List<Map<String, Object>>) edges;
        }
        Object flows = snap.get("flows");
        if (!(flows instanceof List<?> flowList)) return List.of();
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : flowList) {
            if (!(item instanceof Map<?, ?> flow)) continue;
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("from", flow.get("source_ref"));
            edge.put("to", flow.get("target_ref"));
            if (flow.get("condition") != null) edge.put("condition", flow.get("condition"));
            mapped.add(edge);
        }
        return mapped;
    }
    private String writeCanonical(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot canonicalize product-config bundle", ex);
        }
    }

    private static String sha256Hex(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 unavailable", ex);
        }
    }
}