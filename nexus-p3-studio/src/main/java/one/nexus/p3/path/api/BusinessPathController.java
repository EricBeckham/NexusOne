package one.nexus.p3.path.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.path.BusinessPathRecords.DecisionView;
import one.nexus.p3.path.BusinessPathRecords.PathView;
import one.nexus.p3.path.BusinessPathRecords.SubmitStartCommand;
import one.nexus.p3.path.BusinessPathService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/p3/path")
@ConditionalOnProperty(name = "nexus.flow.engine")
@ConditionalOnBean(BusinessPathService.class)
public class BusinessPathController {
    private final BusinessPathService path;

    public BusinessPathController(BusinessPathService path) {
        this.path = path;
    }

    @PostMapping("/submit-start")
    public Map<String, Object> submitAndStart(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody SubmitStartBody body) {
        String key = firstNonBlank(idempotencyKey, body == null ? null : body.idempotencyKey());
        return pathJson(path.submitAndStart(new SubmitStartCommand(
                body.formId(),
                body.values() == null ? Map.of() : body.values(),
                body.processKey(),
                AssigneeType.parse(body.assigneeType()),
                body.assigneeRef(),
                key)));
    }

    @PostMapping("/tasks/{todoId}/approve")
    public Map<String, Object> approve(@PathVariable UUID todoId) {
        return decisionJson(path.approve(todoId));
    }

    @PostMapping("/tasks/{todoId}/reject")
    public Map<String, Object> reject(@PathVariable UUID todoId) {
        return decisionJson(path.reject(todoId));
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    public Map<String, Object> withdraw(@PathVariable UUID instanceId) {
        return decisionJson(path.withdraw(instanceId));
    }

    private static Map<String, Object> pathJson(PathView view) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("submission_id", view.submissionId().toString());
        json.put("form_id", view.formId().toString());
        json.put("ledger_id", view.ledgerId().toString());
        json.put("record_id", view.recordId().toString());
        json.put("ledger_code", view.ledgerCode());
        json.put("physical_table", view.physicalTable());
        json.put("approval_status", view.approvalStatus());
        json.put("record_version", view.recordVersion());
        json.put("idempotent_replay", view.idempotentReplay());
        json.put("process", processJson(view.process()));
        json.put("todo", todoJson(view.todo()));
        return json;
    }

    private static String firstNonBlank(String header, String body) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (body != null && !body.isBlank()) {
            return body.trim();
        }
        return null;
    }

    private static Map<String, Object> decisionJson(DecisionView view) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("ledger_id", view.ledgerId().toString());
        json.put("record_id", view.recordId().toString());
        json.put("physical_table", view.physicalTable());
        json.put("approval_status", view.approvalStatus());
        json.put("process", processJson(view.process()));
        json.put("todo", todoJson(view.todo()));
        return json;
    }

    private static Map<String, Object> processJson(ProcessInstanceView process) {
        if (process == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", process.id().toString());
        json.put("business_key", process.businessKey());
        json.put("status", process.status().name());
        return json;
    }

    private static Map<String, Object> todoJson(TodoView todo) {
        if (todo == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", todo.id().toString());
        json.put("status", todo.status().name());
        json.put("outcome", todo.outcome());
        json.put("assignee_person_id", todo.assigneePersonId() == null ? null : todo.assigneePersonId().toString());
        return json;
    }

    public record SubmitStartBody(
            UUID formId,
            Map<String, Object> values,
            String processKey,
            String assigneeType,
            String assigneeRef,
            String idempotencyKey) {}
}
