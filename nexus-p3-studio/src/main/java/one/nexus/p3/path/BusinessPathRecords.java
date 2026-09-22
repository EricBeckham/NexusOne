package one.nexus.p3.path;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.flow.AssigneeType;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.studio.form.FormSubmission;

public final class BusinessPathRecords {
    private BusinessPathRecords() {}

    public record SubmitStartCommand(
            UUID formId,
            Map<String, Object> values,
            String processKey,
            AssigneeType assigneeType,
            String assigneeRef,
            String idempotencyKey) {
        public SubmitStartCommand(
                UUID formId,
                Map<String, Object> values,
                String processKey,
                AssigneeType assigneeType,
                String assigneeRef) {
            this(formId, values, processKey, assigneeType, assigneeRef, null);
        }
    }

    public record PathView(
            UUID submissionId,
            UUID formId,
            UUID ledgerId,
            UUID recordId,
            String ledgerCode,
            String physicalTable,
            Object approvalStatus,
            FormSubmission submission,
            ProcessInstanceView process,
            TodoView todo,
            Instant at,
            boolean idempotentReplay,
            Object recordVersion) {
        public PathView(
                UUID submissionId,
                UUID formId,
                UUID ledgerId,
                UUID recordId,
                String ledgerCode,
                String physicalTable,
                Object approvalStatus,
                FormSubmission submission,
                ProcessInstanceView process,
                TodoView todo,
                Instant at) {
            this(
                    submissionId,
                    formId,
                    ledgerId,
                    recordId,
                    ledgerCode,
                    physicalTable,
                    approvalStatus,
                    submission,
                    process,
                    todo,
                    at,
                    false,
                    null);
        }
    }

    public record DecisionView(
            UUID ledgerId,
            UUID recordId,
            Object approvalStatus,
            String physicalTable,
            ProcessInstanceView process,
            TodoView todo) {}
}
