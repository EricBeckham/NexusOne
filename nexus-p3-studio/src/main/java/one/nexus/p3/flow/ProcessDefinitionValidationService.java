package one.nexus.p3.flow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionValidationService {
    private final ProcessDefinitionDraftService drafts;
    private final ProcessDefinitionValidator validator;

    public ProcessDefinitionValidationService(ProcessDefinitionDraftService drafts, ProcessDefinitionValidator validator) {
        this.drafts = drafts;
        this.validator = validator;
    }

    public ProcessDefinitionValidationResult validateDraft(String key) {
        return validator.validate(drafts.get(key).bpmnXml());
    }
}
