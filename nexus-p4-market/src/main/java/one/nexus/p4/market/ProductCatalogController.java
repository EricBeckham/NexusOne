package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;
import one.nexus.p4.market.ProductCatalogService.Product;
import one.nexus.p4.market.ProductCatalogService.ProductApp;
import one.nexus.p4.market.ProductCatalogService.ProductView;
import one.nexus.p4.market.ProductCatalogService.ProductWrite;
import one.nexus.p4.market.ProductCatalogService.SkuWrite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Product-native platform design; legacy single-app subscription remains on the existing API until PLB7. */
@RestController
@RequestMapping("/api/v1/p4/products")
public class ProductCatalogController {
    private final ProductCatalogService service;
    public ProductCatalogController(ProductCatalogService service) { this.service = service; }

    @GetMapping public List<Product> list() { return service.list(); }
    @GetMapping("/{id}") public ProductView get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@RequestBody ProductWrite request) { return service.create(request); }
    @PutMapping("/{id}")
    public ProductView update(@PathVariable UUID id, @RequestBody ProductWrite request) {
        return service.update(id, request);
    }
    @PutMapping("/{id}/apps")
    public ProductView replaceApps(@PathVariable UUID id, @RequestBody AppsWrite request) {
        return service.replaceApps(id, request.version(), request.apps());
    }
    @PostMapping("/{id}/publish")
    public ProductView publish(@PathVariable UUID id, @RequestBody Publish request) {
        return service.publish(id, request.version());
    }
    /** PLB6: 下架. A later publish puts an archived product back on the shelf. */
    @PostMapping("/{id}/archive")
    public ProductView archive(@PathVariable UUID id, @RequestBody Publish request) {
        return service.archive(id, request == null ? 0 : request.version());
    }
    @PostMapping("/{id}/skus") @ResponseStatus(HttpStatus.CREATED)
    public String createSku(@PathVariable UUID id, @RequestBody SkuWrite request) {
        return service.createSku(id, request);
    }
    /** PLB8 / D3: product-level switch for same-major automatic upgrades. */
    @PutMapping("/{id}/auto-upgrade-minor")
    public Product setAutoUpgradeMinor(@PathVariable UUID id, @RequestBody AutoUpgrade request) {
        return service.setAutoUpgradeMinor(id, request != null && request.enabled(), request == null ? 0 : request.version());
    }
    public record AppsWrite(long version, List<ProductApp> apps) {}
    public record Publish(long version) {}
    public record AutoUpgrade(long version, boolean enabled) {}
}
