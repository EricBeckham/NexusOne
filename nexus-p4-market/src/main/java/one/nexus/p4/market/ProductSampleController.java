package one.nexus.p4.market;

import java.util.UUID;
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.PrintSample;
import one.nexus.p4.market.ProductSampleModels.ReportSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PC9+: "open a sample" reads for the current product. Read-only; no editor, no tenant data. */
@RestController
@RequestMapping("/api/v1/p4/product-definitions/{id}/samples")
public class ProductSampleController {
    private final ProductSampleService samples;

    public ProductSampleController(ProductSampleService samples) {
        this.samples = samples;
    }

    @GetMapping("/ledger")
    public LedgerSample ledger(@PathVariable UUID id) {
        return samples.ledger(id);
    }

    @GetMapping("/print")
    public PrintSample print(@PathVariable UUID id) {
        return samples.print(id);
    }

    @GetMapping("/report")
    public ReportSample report(@PathVariable UUID id) {
        return samples.report(id);
    }
}