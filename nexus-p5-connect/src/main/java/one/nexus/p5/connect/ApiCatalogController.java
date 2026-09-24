package one.nexus.p5.connect;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only catalog. Does not accept a client tenant id and does not return credentials. */
@RestController
@RequestMapping("/api/v1/p5/api-catalog")
public class ApiCatalogController {
    private final ApiCatalogService catalog;

    public ApiCatalogController(ApiCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ApiCatalogEntry> list() {
        return catalog.list();
    }

    @GetMapping("/{id}")
    public ApiCatalogEntry get(@PathVariable UUID id) {
        return catalog.get(id);
    }
}
