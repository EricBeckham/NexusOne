package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p4.market.MarketModels.ProductConfigReadiness;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ProductDefinitionWrite;
import one.nexus.p4.market.ProductConfigImportModels.ImportAccepted;
import one.nexus.p4.market.ProductConfigImportModels.ImportJob;
import one.nexus.p4.market.ProductConfigImportModels.ImportRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/p4/product-definitions")
public class ProductDefinitionController {
    private final ProductDefinitionService service;
    private final ProductConfigExportService exportService;
    private final ProductConfigImportService importService;

    public ProductDefinitionController(
            ProductDefinitionService service,
            ProductConfigExportService exportService,
            ProductConfigImportService importService) {
        this.service = service;
        this.exportService = exportService;
        this.importService = importService;
    }

    @GetMapping public List<ProductDefinition> list() { return service.list(); }
    @GetMapping("/{id}") public ProductDefinition get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/config-readiness") public ProductConfigReadiness readiness(@PathVariable UUID id) { return service.readiness(id); }

    /** IM4: download product-config format 1.0 JSON bundle. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        Map<String, Object> bundle = exportService.exportBundle(id);
        byte[] body = exportService.toJsonBytes(bundle);
        String filename = exportService.suggestedFilename(bundle);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /** IM5: import package; returns importJobId. Default conflict strategy = skip. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportAccepted importPackage(
            @RequestBody ImportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Map<String, Object> body = request == null ? null : request.packageBody();
        String strategy = request == null ? null : request.conflictStrategy();
        return importService.importPackage(body, strategy, idempotencyKey);
    }

    /** IM6: read import job status / results. */
    @GetMapping("/import-jobs/{id}")
    public ImportJob importJob(@PathVariable UUID id) {
        return importService.getJob(id);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProductDefinition create(@RequestBody ProductDefinitionWrite request) { return service.create(request); }
    @PutMapping("/{id}") public ProductDefinition update(@PathVariable UUID id, @RequestBody ProductDefinitionWrite request) {
        return service.update(id, request);
    }
    @PutMapping("/{id}/skus/{skuCode}") public ProductDefinition bindSku(@PathVariable UUID id, @PathVariable String skuCode) {
        return service.bindSku(skuCode, id);
    }
}