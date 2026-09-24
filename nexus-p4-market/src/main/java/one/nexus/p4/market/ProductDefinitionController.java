package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.MarketModels.ProductDefinitionWrite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/p4/product-definitions")
public class ProductDefinitionController {
    private final ProductDefinitionService service;
    public ProductDefinitionController(ProductDefinitionService service) { this.service = service; }
    @GetMapping public List<ProductDefinition> list() { return service.list(); }
    @GetMapping("/{id}") public ProductDefinition get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProductDefinition create(@RequestBody ProductDefinitionWrite request) { return service.create(request); }
    @PutMapping("/{id}") public ProductDefinition update(@PathVariable UUID id, @RequestBody ProductDefinitionWrite request) {
        return service.update(id, request);
    }
    @PutMapping("/{id}/skus/{skuCode}") public ProductDefinition bindSku(@PathVariable UUID id, @PathVariable String skuCode) {
        return service.bindSku(skuCode, id);
    }
}
