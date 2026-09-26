package one.nexus.p4.market;

import java.util.UUID;
import one.nexus.p4.market.PrintMappingModels.PrintMappingContract;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMappingPut;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CF-R3: print field→slot mapping contract + thin in-memory product override.
 * Zero Flyway; not a print designer.
 */
@RestController
@RequestMapping("/api/v1/p4")
public class PrintMappingController {
    private final PrintMappingService printMapping;

    public PrintMappingController(PrintMappingService printMapping) {
        this.printMapping = printMapping;
    }

    @GetMapping("/print-mapping/contract")
    public PrintMappingContract contract() {
        return printMapping.contract();
    }

    @GetMapping("/product-definitions/{id}/print-mapping")
    public ProductPrintMapping get(@PathVariable UUID id) {
        return printMapping.get(id);
    }

    @PutMapping("/product-definitions/{id}/print-mapping")
    public ProductPrintMapping put(@PathVariable UUID id, @RequestBody ProductPrintMappingPut body) {
        return printMapping.put(id, body);
    }
}