package one.nexus.p3.path.api;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.p3.infra.permission.CatalogSurfaceGuard;
import one.nexus.p3.path.BusinessTypeCatalog;
import one.nexus.p3.path.BusinessTypeProvisioner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/p3/business-types")
public class BusinessTypeController {
    private final BusinessTypeProvisioner provisioner;
    private final ObjectProvider<CatalogSurfaceGuard> surfaces;

    public BusinessTypeController(
            BusinessTypeProvisioner provisioner, ObjectProvider<CatalogSurfaceGuard> surfaces) {
        this.provisioner = provisioner;
        this.surfaces = surfaces;
    }

    @GetMapping
    public Map<String, Object> list() {
        requireRead();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("count", BusinessTypeCatalog.all().size());
        json.put("items", BusinessTypeCatalog.all().stream().map(BusinessTypeProvisioner::typeJson).toList());
        return json;
    }

    @PostMapping("/{code}/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> provision(@PathVariable String code, @RequestBody(required = false) ProvisionRequest body) {
        requireWrite();
        boolean publish = body == null || body.publish() == null || body.publish();
        String suffix = body == null ? null : body.suffix();
        return provisioner.toJson(provisioner.provision(code, suffix, publish));
    }

    private void requireRead() {
        CatalogSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireBusinessTypeRead();
        }
    }

    private void requireWrite() {
        CatalogSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireBusinessTypeWrite();
        }
    }

    public record ProvisionRequest(Boolean publish, String suffix, String appCode) {}
}
