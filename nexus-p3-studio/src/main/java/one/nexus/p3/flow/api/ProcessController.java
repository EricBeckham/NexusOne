package one.nexus.p3.flow.api;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.FlowException;
import one.nexus.p3.flow.ProcessDefinitionCatalog;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.StartCommand;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.SequentialApprovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process")
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessController {
    private final SequentialApprovalService approvals;
    private final one.nexus.p3.flow.ProcessDefinitionPublishService publishing;
    private final ProcessDefinitionCatalog definitions = new ProcessDefinitionCatalog();

    public ProcessController(
            SequentialApprovalService approvals,
            one.nexus.p3.flow.ProcessDefinitionPublishService publishing
    ) {
        this.approvals = approvals;
        this.publishing = publishing;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "nexus-p3-studio", "work_package", "M0-08");
    }

    @GetMapping("/definitions")
    public List<Map<String, Object>> definitions() {
        requireTenant();
        List<Map<String, Object>> all = new ArrayList<>(definitions.list());
        for (var published : publishing.list()) {
            Map<String, Object> definition = new java.util.LinkedHashMap<>(publishing.definition(published.key()));
            definition.put("source", "tenant-deployment");
            definition.put("tenant_id", published.tenantId());
            all.removeIf(existing -> published.key().equals(existing.get("key")));
            all.add(definition);
        }
        return List.copyOf(all);
    }

    @GetMapping("/definitions/{key}")
    public Map<String, Object> definition(@PathVariable String key) {
        requireTenant();
        try {
            Map<String, Object> definition = new java.util.LinkedHashMap<>(publishing.definition(key));
            definition.put("source", "tenant-deployment");
            definition.put("tenant_id", publishing.get(key).tenantId());
            return definition;
        } catch (FlowException missing) {
            if (missing.httpStatus() != 404) throw missing;
            return definitions.get(key);
        }
    }

    @PostMapping("/instances")
    public ProcessInstanceView start(@RequestBody StartBody body) {
        return approvals.start(new StartCommand(
                body.processKey(),
                body.businessKey(),
                AssigneeType.parse(body.assigneeType()),
                body.assigneeRef()));
    }

    @PostMapping("/tasks/{todoId}/approve")
    public TodoView approve(@PathVariable UUID todoId, @RequestBody(required = false) DecisionBody body) {
        return approvals.approve(todoId, commentOf(body));
    }

    @PostMapping("/tasks/{todoId}/reject")
    public TodoView reject(@PathVariable UUID todoId, @RequestBody(required = false) DecisionBody body) {
        return approvals.reject(todoId, commentOf(body));
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    public ProcessInstanceView withdraw(@PathVariable UUID instanceId) {
        return approvals.withdraw(instanceId);
    }

    public record StartBody(String processKey, String businessKey, String assigneeType, String assigneeRef) {}

    /** Optional decision note. Absent, blank, and whitespace-only are the same as no comment. */
    public record DecisionBody(String comment) {}

    private static String commentOf(DecisionBody body) {
        return body == null ? null : body.comment();
    }

    private static void requireTenant() {
        if (TenantContextHolder.get() == null) {
            throw FlowException.missingContext();
        }
    }
}
