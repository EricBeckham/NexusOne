package one.nexus.p3.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mini-product catalog: ledger definition list is not a SurfaceGuard record surface. */
@RestController
@RequestMapping("/api/v1/p3")
public class CatalogOnly {
    @GetMapping("/ledgers")
    public String list() {
        return "catalog";
    }
}
