package one.nexus.p4.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.ProductConfigImportModels.ConflictStrategy;
import one.nexus.p4.market.ProductConfigImportModels.ImportAccepted;
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;
import one.nexus.p4.market.ProductConfigImportModels.JobStatus;
import one.nexus.p4.market.ProductConfigImportModels.ResourceResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * IM5: validate + import product-config format 1.0 with conflict strategy and Idempotency-Key.
 * Never silently overwrites a PUBLISHED product definition.
 */
@Service
@ConditionalOnBean({IsolationGuard.class, AuditPort.class, ClockPort.class})
public class ProductConfigImportService {
    private final MarketRepository market;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final ProductConfigImportJobStore jobs;
    private final UUID platformTenantId;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public ProductConfigImportService(
            MarketRepository market,
            IsolationGuard isolation,
            AuditPort audit,
            ClockPort clock,
            ProductConfigImportJobStore jobs,
            P1Properties properties) {
        this.market = market;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.jobs = jobs;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    @Transactional
    public ImportAccepted importPackage(Map<String, Object> packageBody, String conflictStrategy, String idempotencyKey) {
        PlatformContext actor = isolation.requirePlatformContext("product-definition.import", PlatformDuty.SYSTEM_ADMIN);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = jobs.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                return new ImportAccepted(existing.get().id(), existing.get().status());
            }
        }
        ConflictStrategy strategy = parseStrategy(conflictStrategy);
        UUID jobId = UUID.randomUUID();
        Instant now = clock.now();
        try {
            validatePackage(packageBody);
            List<ResourceResult> results = new ArrayList<>();
            applyProductDefinition(packageBody, strategy, results);
            Map<String, Object> counts = countsOf(results);
            String checksum = checksumOf(packageBody);
            ImportJob job = new ImportJob(jobId, JobStatus.COMPLETED, strategy, blankToNull(idempotencyKey),
                    checksum, now, clock.now(), null, results, counts);
            jobs.save(job);
            audit.append(new AuditEvent(
                    UUID.randomUUID(), "platform.product_definition.imported", AuditEvent.VERSION, clock.now(),
                    actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                    actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                    "product_definition_import", jobId.toString(),
                    Map.of("strategy", strategy.name(), "counts", counts)));
            return new ImportAccepted(jobId, JobStatus.COMPLETED);
        } catch (ResponseStatusException ex) {
            ImportJob failed = new ImportJob(jobId, JobStatus.FAILED, strategy, blankToNull(idempotencyKey),
                    checksumOf(packageBody), now, clock.now(), ex.getReason(), List.of(), Map.of());
            jobs.save(failed);
            audit.append(new AuditEvent(
                    UUID.randomUUID(), "platform.product_definition.import_failed", AuditEvent.VERSION, clock.now(),
                    actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                    actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_REJECTED,
                    "product_definition_import", jobId.toString(),
                    Map.of("reason", String.valueOf(ex.getReason()))));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ImportJob getJob(UUID id) {
        isolation.requirePlatformContext("product-definition.import-job.get", PlatformDuty.SYSTEM_ADMIN);
        return jobs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "import job not found"));
    }

    private void applyProductDefinition(Map<String, Object> packageBody, ConflictStrategy strategy, List<ResourceResult> results) {
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) packageBody.get("productDefinition");
        String code = text(product.get("code"));
        String name = text(product.get("name"));
        String description = text(product.get("description"));
        String formCode = text(product.get("formTemplateCode"));
        Integer formVersion = intOrNull(product.get("formTemplateVersion"));
        String processKey = text(product.get("processDefinitionKey"));
        Integer processVersion = intOrNull(product.get("processDefinitionVersion"));
        if (code == null || name == null || formCode == null || formVersion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productDefinition requires code, name, formTemplateCode, formTemplateVersion");
        }
        UUID formVersionId = market.publishedFormTemplateVersionByCode(formCode, formVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "form template " + formCode + " v" + formVersion + " is not published in this environment"));
        if (processKey != null && processVersion == null || processKey == null && processVersion != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "process key and version must be supplied together");
        }

        var existing = market.productDefinitionByCode(code);
        if (existing.isPresent()) {
            ProductDefinition current = existing.get();
            if ("PUBLISHED".equalsIgnoreCase(current.status()) || "ARCHIVED".equalsIgnoreCase(current.status())) {
                switch (strategy) {
                    case FAIL -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "product definition '" + code + "' already published; refuse overwrite");
                    case SKIP -> {
                        results.add(new ResourceResult("productDefinition", code, "SKIPPED", "已存在已发布定义，按 skip 跳过"));
                        return;
                    }
                    case NEW_VERSION -> {
                        String forkCode = uniqueForkCode(code);
                        ProductDefinition created = saveDraft(forkCode, name, description, formVersionId, processKey, processVersion);
                        results.add(new ResourceResult("productDefinition", forkCode, "CREATED",
                                "已发布定义未覆盖；已创建旁路草稿 " + forkCode));
                        return;
                    }
                }
            }
            // DRAFT exists
            switch (strategy) {
                case FAIL -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "product definition '" + code + "' already exists");
                case SKIP -> {
                    results.add(new ResourceResult("productDefinition", code, "SKIPPED", "草稿已存在，按 skip 跳过"));
                    return;
                }
                case NEW_VERSION -> {
                    ProductDefinition next = new ProductDefinition(
                            current.id(), current.code(), name, description, formVersionId, processKey, processVersion,
                            current.defaultPermissionPack(), "DRAFT", current.createdAt(), clock.now(), current.version() + 1);
                    market.saveProductDefinition(next);
                    results.add(new ResourceResult("productDefinition", code, "UPDATED", "已更新既有草稿（未发布）"));
                    return;
                }
            }
        }
        ProductDefinition created = saveDraft(code, name, description, formVersionId, processKey, processVersion);
        results.add(new ResourceResult("productDefinition", created.code(), "CREATED", "已创建草稿产品定义"));
    }

    private ProductDefinition saveDraft(
            String code, String name, String description, UUID formVersionId, String processKey, Integer processVersion) {
        Instant now = clock.now();
        ProductDefinition definition = new ProductDefinition(
                UUID.randomUUID(), code, name, description, formVersionId, processKey, processVersion,
                Map.of(), "DRAFT", now, now, 1);
        market.saveProductDefinition(definition);
        return definition;
    }

    private String uniqueForkCode(String code) {
        for (int i = 2; i < 100; i++) {
            String candidate = code + ".import" + i;
            if (market.productDefinitionByCode(candidate).isEmpty()) return candidate;
        }
        return code + ".import-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @SuppressWarnings("unchecked")
    private void validatePackage(Map<String, Object> packageBody) {
        if (packageBody == null || packageBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "package body required");
        }
        Object manifestObj = packageBody.get("manifest");
        if (!(manifestObj instanceof Map<?, ?> manifest)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest required");
        }
        if (!"nexusone.product-config".equals(String.valueOf(manifest.get("format")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported package format");
        }
        if (!"1.0".equals(String.valueOf(manifest.get("formatVersion")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported formatVersion");
        }
        if (!(packageBody.get("productDefinition") instanceof Map<?, ?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productDefinition required");
        }
        if (!(packageBody.get("formTemplates") instanceof List<?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formTemplates required");
        }
        if (!(packageBody.get("processDefinitions") instanceof List<?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "processDefinitions required");
        }
        if (!(packageBody.get("skuBindings") instanceof List<?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skuBindings required");
        }
    }

    private static ConflictStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) return ConflictStrategy.SKIP;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "SKIP", "SKIP_EXISTING" -> ConflictStrategy.SKIP;
            case "NEW_VERSION", "NEWVERSION" -> ConflictStrategy.NEW_VERSION;
            case "FAIL" -> ConflictStrategy.FAIL;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conflictStrategy must be skip|new-version|fail");
        };
    }

    private static Map<String, Object> countsOf(List<ResourceResult> results) {
        long created = results.stream().filter(r -> "CREATED".equals(r.outcome())).count();
        long updated = results.stream().filter(r -> "UPDATED".equals(r.outcome())).count();
        long skipped = results.stream().filter(r -> "SKIPPED".equals(r.outcome())).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.outcome())).count();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("created", created);
        counts.put("updated", updated);
        counts.put("skipped", skipped);
        counts.put("failed", failed);
        return counts;
    }

    private String checksumOf(Map<String, Object> packageBody) {
        if (packageBody == null) return null;
        Object manifest = packageBody.get("manifest");
        if (manifest instanceof Map<?, ?> map && map.get("checksum") != null) {
            return String.valueOf(map.get("checksum"));
        }
        try {
            return "sha256:inline:" + Integer.toHexString(json.writeValueAsString(packageBody).hashCode());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}