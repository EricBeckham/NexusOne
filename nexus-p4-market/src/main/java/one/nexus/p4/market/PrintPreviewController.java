package one.nexus.p4.market;

import java.util.UUID;
import one.nexus.p4.market.PrintPreviewModels.PrintPreview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DPO-L5: read-only print preview payloads (no PDF). */
@RestController
@RequestMapping("/api/v1/p4")
public class PrintPreviewController {
    private final PrintPreviewService previews;

    public PrintPreviewController(PrintPreviewService previews) {
        this.previews = previews;
    }

    /** Platform: the product's layout with its saved mapping; no application, all values empty. */
    @GetMapping("/product-definitions/{id}/print-preview")
    public PrintPreview product(@PathVariable UUID id) {
        return previews.forProduct(id);
    }

    /** Tenant: one application of the caller's own tenant filled through the mapping. */
    @GetMapping("/applications/{applicationId}/print-preview")
    public PrintPreview application(@PathVariable UUID applicationId) {
        return previews.forApplication(applicationId);
    }
}
