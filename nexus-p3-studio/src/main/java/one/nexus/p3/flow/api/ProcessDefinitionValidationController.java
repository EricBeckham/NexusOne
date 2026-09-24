package one.nexus.p3.flow.api;

import one.nexus.p3.flow.ProcessDefinitionValidationResult;
import one.nexus.p3.flow.ProcessDefinitionValidationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process/drafts")
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionValidationController {
    private final ProcessDefinitionValidationService validation;

    public ProcessDefinitionValidationController(ProcessDefinitionValidationService validation) {
        this.validation = validation;
    }

    @PostMapping("/{key}/validate")
    public ProcessDefinitionValidationResult validate(@PathVariable String key) {
        return validation.validateDraft(key);
    }
}
