package one.nexus.p3.flow;

import java.util.List;

public record ProcessDefinitionValidationResult(boolean valid, List<ValidationError> errors) {
    public ProcessDefinitionValidationResult {
        errors = List.copyOf(errors);
    }

    public record ValidationError(String code, String message, String nodeId, String flowId) {}
}
