package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p4.market.AppDesignService.Category;
import one.nexus.p4.market.AppDesignService.CategoryWrite;
import one.nexus.p4.market.AppDesignService.ManifestSnapshot;
import one.nexus.p4.market.AppDesignService.Validation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform design API; does not expose tenant-scoped writes. */
@RestController
@RequestMapping("/api/v1/p4")
public class AppDesignController {
    private final AppDesignService service;
    public AppDesignController(AppDesignService service) { this.service = service; }

    @GetMapping("/app-categories") public List<Category> categories() { return service.categories(); }
    @GetMapping("/app-categories/{code}") public Category category(@PathVariable String code) { return service.category(code); }
    @PostMapping("/app-categories/{code}") @ResponseStatus(HttpStatus.CREATED)
    public Category createCategory(@PathVariable String code, @RequestBody CategoryWrite request) {
        return service.createCategory(code, request);
    }
    @PutMapping("/app-categories/{code}")
    public Category updateCategory(@PathVariable String code, @RequestBody CategoryWrite request) {
        return service.updateCategory(code, request);
    }
    @DeleteMapping("/app-categories/{code}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String code) { service.deleteCategory(code); }
    @PutMapping("/apps/{appId}/category") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAppCategory(@PathVariable UUID appId, @RequestBody CategoryRef request) {
        service.setAppCategory(appId, request == null ? null : request.categoryCode());
    }
    @GetMapping("/versions/{versionId}/manifest")
    public ManifestSnapshot manifest(@PathVariable UUID versionId) { return service.manifest(versionId); }
    @PostMapping("/versions/{versionId}/manifest/validate")
    public Validation validate(@PathVariable UUID versionId, @RequestBody Map<String, Object> manifest) {
        return service.validate(versionId, manifest);
    }
    @PutMapping("/versions/{versionId}/manifest")
    public ManifestSnapshot saveManifest(@PathVariable UUID versionId, @RequestBody Map<String, Object> manifest) {
        return service.saveManifest(versionId, manifest);
    }
    public record CategoryRef(String categoryCode) {}
}
