package one.nexus.p4.market;

import java.util.List;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplateCopy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** DPO-L4: print template metadata (built-in + copies). Not a layout designer. */
@RestController
@RequestMapping("/api/v1/p4/print-templates")
public class PrintTemplateController {
    private final PrintTemplateService templates;

    public PrintTemplateController(PrintTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<PrintTemplate> list() {
        return templates.list();
    }

    @PostMapping("/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public PrintTemplate copy(@RequestBody(required = false) PrintTemplateCopy body) {
        return templates.copy(body);
    }
}
