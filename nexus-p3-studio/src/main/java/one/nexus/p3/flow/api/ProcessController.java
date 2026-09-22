package one.nexus.p3.flow.api;

import java.util.Map;
import java.util.UUID;
import one.nexus.p3.flow.AssigneeType;
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

    public ProcessController(SequentialApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "nexus-p3-studio", "work_package", "M0-08");
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
    public TodoView approve(@PathVariable UUID todoId) {
        return approvals.approve(todoId);
    }

    @PostMapping("/tasks/{todoId}/reject")
    public TodoView reject(@PathVariable UUID todoId) {
        return approvals.reject(todoId);
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    public ProcessInstanceView withdraw(@PathVariable UUID instanceId) {
        return approvals.withdraw(instanceId);
    }

    public record StartBody(String processKey, String businessKey, String assigneeType, String assigneeRef) {}
}
