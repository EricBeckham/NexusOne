package one.nexus.p3.flow.api;

import one.nexus.p3.flow.ProcessDefinitionValidationResult;
import one.nexus.p3.flow.ProcessDefinitionValidationService;
import one.nexus.p3.infra.permission.ProcessDefinitionSurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<ProcessDefinitionSurfaceGuard> surfaces;

    public ProcessDefinitionValidationController(
            ProcessDefinitionValidationService validation,
            ObjectProvider<ProcessDefinitionSurfaceGuard> surfaces) {
        this.validation = validation;
        this.surfaces = surfaces;
    }

    @PostMapping("/{key}/validate")
    public ProcessDefinitionValidationResult validate(@PathVariable String key) {
        ProcessDefinitionSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireWrite();
        }
        return validation.validateDraft(key);
    }
}
